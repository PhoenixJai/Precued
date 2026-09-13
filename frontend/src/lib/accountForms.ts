const EMAIL_SHAPE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export interface SignUpDraft {
  email: string;
  password: string;
  displayName: string;
}

/** Client-side mirror of SignUpRequest's validation annotations — immediate feedback; the server re-validates independently. */
export function validateSignUp(draft: SignUpDraft): string | null {
  if (!draft.email.trim()) return "Email is required.";
  if (!EMAIL_SHAPE.test(draft.email.trim())) return "Enter a valid email address.";
  if (!draft.displayName.trim()) return "Display name is required.";
  if (draft.password.length < 8) return "Password must be at least 8 characters.";
  return null;
}

export interface LogInDraft {
  email: string;
  password: string;
}

export function validateLogIn(draft: LogInDraft): string | null {
  if (!draft.email.trim()) return "Email is required.";
  if (!draft.password) return "Password is required.";
  return null;
}
