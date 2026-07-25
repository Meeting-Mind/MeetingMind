package loader

import "errors"

type Error struct {
	code  string
	cause error
}

func (e *Error) Error() string {
	return e.code
}

func (e *Error) Unwrap() error {
	return e.cause
}

func fail(code string, cause error) error {
	return &Error{code: code, cause: cause}
}

func CodeOf(err error) string {
	var loaderError *Error
	if errors.As(err, &loaderError) {
		return loaderError.code
	}
	return "internal_failure"
}
