-- Los dos planes incluyen videos cortos; Plus conserva la analítica avanzada.
UPDATE planes SET incluye_video = TRUE WHERE nombre IN ('pro', 'elite');
