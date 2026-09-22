package com.checkbiz.backend.service

/** Plantilla compartida de verificación y recuperación, con logo PNG adjunto por CID. */
object EmailTemplates {
    fun codigoVerificacion(
        nombreCompleto: String,
        etiqueta: String,
        mensaje: String,
        codigo: String,
    ): String = """
        <!DOCTYPE html>
        <html lang="es">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <meta name="color-scheme" content="light">
        <title>CheckBiz</title>
        <style>
          @keyframes cbPopIn {
            0% { transform: scale(.85); opacity: 0; }
            60% { transform: scale(1.05); opacity: 1; }
            100% { transform: scale(1); opacity: 1; }
          }
          @keyframes cbPulse {
            0% { box-shadow: 0 0 0 0 rgba(47,99,224,.35); }
            70% { box-shadow: 0 0 0 14px rgba(47,99,224,0); }
            100% { box-shadow: 0 0 0 0 rgba(47,99,224,0); }
          }
          .cb-shield { animation: cbPopIn .5s ease-out; }
          .cb-code { animation: cbPopIn .6s ease-out, cbPulse 2.4s ease-out .6s 2; }
        </style>
        </head>
        <body style="margin:0;padding:0;background-color:#EFE9DA;">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#EFE9DA;">
        <tr><td align="center" style="padding:32px 16px;">
          <table role="presentation" width="480" cellpadding="0" cellspacing="0" style="max-width:480px;width:100%;background-color:#FFFCF6;border-radius:20px;overflow:hidden;box-shadow:0 8px 28px rgba(27,42,51,.12);font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">

            <!-- Header -->
            <tr><td style="background-color:#172A36;padding:36px 32px 28px;text-align:center;">
              <img src="cid:checkbiz-logo" alt="Logo de CheckBiz" width="90" height="80" style="display:block;margin:0 auto;width:90px;height:80px;border:0;border-radius:14px;">
              <div style="margin-top:14px;font-size:22px;font-weight:700;color:#ffffff;letter-spacing:.3px;">CheckBiz</div>
              <div style="margin-top:4px;font-size:11px;color:#9FB4C4;letter-spacing:.8px;">CONFIANZA VERIFICADA, SIN INTERMEDIARIOS</div>
            </td></tr>

            <!-- Etiqueta -->
            <tr><td style="background-color:#E7EEFD;padding:13px 32px;text-align:center;">
              <span style="font-size:11.5px;font-weight:700;color:#2F63E0;letter-spacing:.7px;text-transform:uppercase;">$etiqueta</span>
            </td></tr>

            <!-- Cuerpo -->
            <tr><td style="padding:30px 32px 6px;">
              <p style="margin:0 0 6px;font-size:15px;color:#1B2A33;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">Hola <strong>$nombreCompleto</strong>,</p>
              <p style="margin:0;font-size:13.5px;color:#5B6B72;line-height:1.6;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">$mensaje</p>
            </td></tr>

            <!-- Código -->
            <tr><td style="padding:22px 32px 26px;text-align:center;">
              <table role="presentation" cellpadding="0" cellspacing="0" align="center" class="cb-code" style="background-color:#F2F6FF;border:1px solid #D6E2FA;border-radius:16px;">
                <tr><td style="padding:18px 34px;">
                  <span style="font-family:'Courier New',Courier,monospace;font-size:32px;font-weight:700;letter-spacing:9px;color:#172A36;">$codigo</span>
                </td></tr>
              </table>
            </td></tr>

            <!-- Aviso de vencimiento -->
            <tr><td style="padding:0 32px 30px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#FBEEDC;border-radius:10px;">
                <tr><td style="padding:12px 16px;font-size:12px;color:#C87A1E;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                  &#9201; Vence en 5 minutos. Si no fuiste tú, ignora este correo — tu cuenta sigue segura.
                </td></tr>
              </table>
            </td></tr>

            <!-- Footer -->
            <tr><td style="background-color:#F5F0E4;padding:18px 32px;text-align:center;border-top:1px solid #E6DFCE;">
              <p style="margin:0;font-size:10.5px;color:#8A968C;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">Business Week — UEES · Prototipo académico</p>
              <p style="margin:4px 0 0;font-size:10.5px;color:#8A968C;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">Correo automático — no respondas a esta dirección.</p>
            </td></tr>

          </table>
        </td></tr>
        </table>
        </body>
        </html>
    """.trimIndent()
}
