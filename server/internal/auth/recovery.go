package auth

import (
	"crypto/rand"
	"math/big"
	"strings"
)

// Crockford base32 without I, L, O, U: no character can be misread by a human
// copying the code off a screen onto paper.
const recoveryAlphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

const (
	recoveryGroups      = 5
	recoveryGroupLength = 5
)

// NewRecoveryCode returns a code like NUVA-4K7Q2-... . It is shown to the user
// exactly once at registration; the server keeps only a bcrypt hash of it.
func NewRecoveryCode() (string, error) {
	groups := make([]string, 0, recoveryGroups+1)
	groups = append(groups, "NUVA")

	max := big.NewInt(int64(len(recoveryAlphabet)))
	for g := 0; g < recoveryGroups; g++ {
		var sb strings.Builder
		for i := 0; i < recoveryGroupLength; i++ {
			n, err := rand.Int(rand.Reader, max)
			if err != nil {
				return "", err
			}
			sb.WriteByte(recoveryAlphabet[n.Int64()])
		}
		groups = append(groups, sb.String())
	}
	return strings.Join(groups, "-"), nil
}

// NormalizeRecoveryCode makes user input comparable: upper case, no spaces,
// and the common 0/O and 1/I/L mistypes folded onto the canonical characters.
func NormalizeRecoveryCode(input string) string {
	s := strings.ToUpper(strings.TrimSpace(input))
	s = strings.NewReplacer(" ", "", "\t", "", "\n", "").Replace(s)
	s = strings.NewReplacer("O", "0", "I", "1", "L", "1", "U", "V").Replace(s)
	return s
}
