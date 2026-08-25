package api

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/google/uuid"

	"github.com/nuva/server/internal/store"
)

// How much of a multipart form we are willing to buffer in RAM. Anything
// larger spills to the OS temp dir, which is what we want for audio.
const multipartMemory = 8 << 20 // 8 MiB

// A voice note is capped well below the generic upload limit. Ten minutes of
// Opus at 32 kbps is about 2.4 MB, so 16 MB leaves a wide margin while still
// making an accidental hour-long recording fail fast.
const maxVoiceBytes = 16 << 20

// maxWaveformBars is the widest envelope the UI can draw. More bars than this
// is wasted bytes on the wire, so we reject rather than silently truncate.
const maxWaveformBars = 256

// mimeByKind is an allowlist, not a blocklist. Anything not named here is
// refused, so a new file type is always a deliberate decision.
var mimeByKind = map[string]map[string]string{
	"voice": {
		"audio/ogg":  ".ogg",
		"audio/opus": ".opus",
		"audio/mp4":  ".m4a",
		"audio/aac":  ".aac",
		"audio/mpeg": ".mp3",
	},
	"image": {
		"image/jpeg": ".jpg",
		"image/png":  ".png",
		"image/webp": ".webp",
	},
}

// mediaResponse is what the client gets back and then quotes verbatim in the
// send_voice frame. Nothing here is secret, so it is safe to cache.
type mediaResponse struct {
	ID         string  `json:"id"`
	URL        string  `json:"url"`
	Kind       string  `json:"kind"`
	Mime       string  `json:"mime"`
	Bytes      int64   `json:"bytes"`
	DurationMs int32   `json:"duration_ms"`
	Waveform   []int16 `json:"waveform"`
	Reused     bool    `json:"reused"`
}

// handleUploadMedia accepts one multipart file and stores it content-addressed.
//
// Form fields:
//
//	file        the bytes            (required)
//	kind        voice | image        (required)
//	duration_ms integer, voice only  (required for voice)
//	waveform    comma-separated 0..100 ints, voice only
//
// The audio is never decoded here on purpose: the recorder already knows the
// duration and the envelope, and adding a codec to the server would make every
// upload cost CPU and add a dependency we would have to keep patched.
func (s *Server) handleUploadMedia(w http.ResponseWriter, r *http.Request) {
	userID, ok := UserIDFrom(r.Context())
	if !ok {
		writeError(w, http.StatusUnauthorized, "invalid_token", "token is invalid")
		return
	}

	limit := s.cfg.MaxUploadBytes
	if limit <= 0 {
		limit = maxVoiceBytes
	}
	// +1 so a file exactly on the limit is accepted and one byte over is not.
	r.Body = http.MaxBytesReader(w, r.Body, limit+1)

	if err := r.ParseMultipartForm(multipartMemory); err != nil {
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			writeError(w, http.StatusRequestEntityTooLarge, "file_too_large",
				fmt.Sprintf("upload must be at most %d bytes", limit))
			return
		}
		writeError(w, http.StatusBadRequest, "invalid_body", "expected a multipart/form-data upload")
		return
	}
	defer func() { _ = r.MultipartForm.RemoveAll() }()

	kind := strings.TrimSpace(r.FormValue("kind"))
	allowed, known := mimeByKind[kind]
	if !known {
		writeError(w, http.StatusUnprocessableEntity, "invalid_kind", "kind must be one of: voice, image")
		return
	}

	file, header, err := r.FormFile("file")
	if err != nil {
		writeError(w, http.StatusBadRequest, "missing_file", "form field 'file' is required")
		return
	}
	defer func() { _ = file.Close() }()

	mime := normalizeMime(header.Header.Get("Content-Type"))
	ext, mimeOK := allowed[mime]
	if !mimeOK {
		writeError(w, http.StatusUnsupportedMediaType, "unsupported_mime",
			fmt.Sprintf("%q is not an accepted content type for kind %q", mime, kind))
		return
	}

	if kind == "voice" && limit > maxVoiceBytes {
		limit = maxVoiceBytes
	}

	durationMs, waveform, verr := parseVoiceFields(r, kind)
	if verr != "" {
		writeError(w, http.StatusUnprocessableEntity, "invalid_voice_metadata", verr)
		return
	}

	// Stream to a temp file while hashing, so a large upload never sits in RAM
	// and we learn the digest without a second pass over the bytes.
	tmp, err := os.CreateTemp(s.cfg.MediaDir, ".upload-*")
	if err != nil {
		// Most likely the media dir does not exist or is not writable, which is
		// a deployment problem worth its own log line.
		s.internalError(w, "create temp upload file", err)
		return
	}
	tmpPath := tmp.Name()
	// Removed unless we deliberately keep the file below.
	keep := false
	defer func() {
		_ = tmp.Close()
		if !keep {
			_ = os.Remove(tmpPath)
		}
	}()

	digest := sha256.New()
	written, err := io.Copy(io.MultiWriter(tmp, digest), io.LimitReader(file, limit+1))
	if err != nil {
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			writeError(w, http.StatusRequestEntityTooLarge, "file_too_large",
				fmt.Sprintf("upload must be at most %d bytes", limit))
			return
		}
		s.internalError(w, "buffer upload", err)
		return
	}
	if written == 0 {
		writeError(w, http.StatusUnprocessableEntity, "empty_file", "the uploaded file is empty")
		return
	}
	if written > limit {
		writeError(w, http.StatusRequestEntityTooLarge, "file_too_large",
			fmt.Sprintf("upload must be at most %d bytes", limit))
		return
	}
	if err := tmp.Sync(); err != nil {
		s.internalError(w, "flush upload", err)
		return
	}

	sum := hex.EncodeToString(digest.Sum(nil))
	relPath := shardedPath(kind, sum, ext)
	absPath := filepath.Join(s.cfg.MediaDir, relPath)

	if err := os.MkdirAll(filepath.Dir(absPath), 0o755); err != nil {
		s.internalError(w, "create media directory", err)
		return
	}
	if err := os.Rename(tmpPath, absPath); err != nil {
		s.internalError(w, "move upload into place", err)
		return
	}
	keep = true

	att, reused, err := s.db.PutAttachment(r.Context(), store.CreateAttachmentParams{
		OwnerID:     userID,
		SHA256:      sum,
		Kind:        kind,
		Mime:        mime,
		Bytes:       written,
		DurationMs:  durationMs,
		Waveform:    waveform,
		StoragePath: relPath,
	})
	if err != nil {
		// The row failed but the file is on disk. Remove it so a retry is clean
		// rather than leaving an orphan nothing will ever reference.
		_ = os.Remove(absPath)
		s.internalError(w, "record attachment", err)
		return
	}

	writeJSON(w, http.StatusCreated, mediaResponse{
		ID:         att.ID.String(),
		URL:        "/" + APIVersion + "/media/" + att.ID.String(),
		Kind:       att.Kind,
		Mime:       att.Mime,
		Bytes:      att.Bytes,
		DurationMs: att.DurationMs,
		Waveform:   att.Waveform,
		Reused:     reused,
	})
}

// handleGetMedia streams a stored blob back. Auth is required: media URLs are
// not secret, but they are also not public, and http.ServeContent gives us
// range requests for free, which is what makes seeking in a voice note work.
func (s *Server) handleGetMedia(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(chiURLParam(r, "id"))
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid_id", "media id is not a uuid")
		return
	}

	att, err := s.db.AttachmentByID(r.Context(), id)
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "media_not_found", "no media with this id")
		return
	}
	if err != nil {
		s.internalError(w, "load attachment", err)
		return
	}

	// Join through filepath.Clean and re-check the prefix: storage_path comes
	// from our own writer, but a path traversal here would be catastrophic, so
	// it is verified rather than trusted.
	abs := filepath.Join(s.cfg.MediaDir, filepath.Clean("/"+att.StoragePath))
	if !strings.HasPrefix(abs, filepath.Clean(s.cfg.MediaDir)+string(os.PathSeparator)) {
		s.internalError(w, "media path escaped media dir", errors.New(att.StoragePath))
		return
	}

	f, err := os.Open(abs)
	if err != nil {
		s.internalError(w, "open media file", err)
		return
	}
	defer func() { _ = f.Close() }()

	info, err := f.Stat()
	if err != nil {
		s.internalError(w, "stat media file", err)
		return
	}

	w.Header().Set("Content-Type", att.Mime)
	// Content-addressed, so the bytes behind an id can never change.
	w.Header().Set("Cache-Control", "private, max-age=31536000, immutable")
	w.Header().Set("ETag", `"`+att.SHA256+`"`)
	http.ServeContent(w, r, filepath.Base(abs), info.ModTime(), f)
}

// parseVoiceFields validates the two voice-only form fields. Returns a
// human-readable reason on failure, empty string on success.
func parseVoiceFields(r *http.Request, kind string) (int32, []int16, string) {
	if kind != "voice" {
		return 0, []int16{}, ""
	}

	raw := strings.TrimSpace(r.FormValue("duration_ms"))
	if raw == "" {
		return 0, nil, "duration_ms is required for a voice upload"
	}
	ms, err := strconv.ParseInt(raw, 10, 32)
	if err != nil || ms <= 0 {
		return 0, nil, "duration_ms must be a positive integer"
	}

	waveform := make([]int16, 0, maxWaveformBars)
	for _, part := range strings.Split(r.FormValue("waveform"), ",") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		v, err := strconv.Atoi(part)
		if err != nil || v < 0 || v > 100 {
			return 0, nil, "waveform must be comma-separated integers in 0..100"
		}
		waveform = append(waveform, int16(v))
		if len(waveform) > maxWaveformBars {
			return 0, nil, fmt.Sprintf("waveform must have at most %d bars", maxWaveformBars)
		}
	}

	return int32(ms), waveform, ""
}

// normalizeMime drops any parameters, so "audio/ogg; codecs=opus" matches the
// allowlist key "audio/ogg".
func normalizeMime(raw string) string {
	if i := strings.IndexByte(raw, ';'); i >= 0 {
		raw = raw[:i]
	}
	return strings.ToLower(strings.TrimSpace(raw))
}

// shardedPath spreads files over 256 directories so no single directory ever
// holds a million entries, which some filesystems handle very badly.
func shardedPath(kind, sum, ext string) string {
	return filepath.Join(kind, sum[0:2], sum[2:4], sum+ext)
}
