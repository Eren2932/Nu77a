package api

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"

	"github.com/go-chi/chi/v5"
)

const maxJSONBody = 1 << 20 // 1 MiB is plenty for any JSON endpoint

type errorBody struct {
	Error struct {
		Code    string `json:"code"`
		Message string `json:"message"`
	} `json:"error"`
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	if payload == nil {
		return
	}
	if err := json.NewEncoder(w).Encode(payload); err != nil {
		slog.Error("write json response", "err", err)
	}
}

// writeError always answers with the same shape so the client has exactly one
// error parser to maintain.
func writeError(w http.ResponseWriter, status int, code, message string) {
	var body errorBody
	body.Error.Code = code
	body.Error.Message = message
	writeJSON(w, status, body)
}

func decodeJSON(w http.ResponseWriter, r *http.Request, dst any) bool {
	r.Body = http.MaxBytesReader(w, r.Body, maxJSONBody)
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()

	if err := dec.Decode(dst); err != nil {
		writeError(w, http.StatusBadRequest, "invalid_body", "request body is not valid JSON: "+err.Error())
		return false
	}
	// Reject trailing garbage after the JSON object.
	if err := dec.Decode(&struct{}{}); err != io.EOF {
		writeError(w, http.StatusBadRequest, "invalid_body", "request body must contain a single JSON object")
		return false
	}
	return true
}

// chiURLParam exists so handlers do not each import chi just to read one path
// segment. Keeping the import in one file makes the router easier to swap.
func chiURLParam(r *http.Request, key string) string {
	return chi.URLParam(r, key)
}
