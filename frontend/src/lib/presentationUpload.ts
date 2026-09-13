/** Matches precued.pdf.max-file-size-bytes (backend) — a UX nicety, not a security boundary; the server enforces the real cap. */
export const MAX_PRESENTATION_UPLOAD_BYTES = 25 * 1024 * 1024;

/** Structural, not File itself — lets a plain object stand in for a browser File in tests. */
export interface UploadCandidateFile {
  type: string;
  name: string;
  size: number;
}

/** Client-side pre-check before ever hitting the network, shared by a fresh upload and a mid-share replace. */
export function validatePresentationFile(file: UploadCandidateFile): string | null {
  const looksLikePdf = file.type === "application/pdf" || file.name.toLowerCase().endsWith(".pdf");
  if (!looksLikePdf) return "Please choose a PDF file.";
  if (file.size > MAX_PRESENTATION_UPLOAD_BYTES) {
    return "This file is larger than 25 MB and the server will likely reject it.";
  }
  return null;
}

export function derivePresentationLabel(fileName: string): string {
  return fileName.replace(/\.pdf$/i, "") || "Presentation";
}
