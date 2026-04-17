# Desarrollo UberSDR Android – Flujo de trabajo

## Estructura del proyecto

- Repo raíz: `/mnt/d/dev/ka9q_ubersdr`
- Cliente Android: `/mnt/d/dev/ka9q_ubersdr/android-client`
- Documentación: `/mnt/d/dev/ka9q_ubersdr/docs/architecture`

---

## Modelo de trabajo

- Codex trabaja en WSL (edita código)
- Android Studio trabaja en Windows (compila y ejecuta)
- Git sincroniza todo

### Separación de responsabilidades

- Codex → edición de código  
- Android Studio → ejecución  
- Git → control  

---

## Directorio de trabajo

⚠️ IMPORTANTE

Siempre trabajar desde:

```bash
cd /mnt/d/dev/ka9q_ubersdr/android-client
