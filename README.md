# FRewards
# FRewards 🌟

**FRewards** es un plugin de recompensas diarias totalmente personalizable para servidores Paper/Spigot 1.21+. Ofrece una interfaz moderna, sistema de permisos, cooldowns configurables, y compatibilidad con PlaceholderAPI.

---

## ✨ Características principales

* ✔ Menú de recompensas estilo GUI totalmente configurable
* ✔ Compatibilidad con [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
* ✔ Cooldowns por recompensa
* ✔ Items y comandos como recompensas
* ✔ Soporte para hex colors (`&#FF0000`) y `&a` style
* ✔ Actualización en tiempo real de las recompensas sin reiniciar

---

## 📂 Archivos

* `plugin.yml` - Configuración básica de comandos y permisos
* `rewardsgui.yml` - GUI de recompensas, materiales, textos, sonidos y comandos
* `messages.yml` - Mensajes configurables
* `data.yml` - Cooldowns por jugador (generado automáticamente)

---

## ⚖ Comandos

| Comando                    | Descripción                                 |
| -------------------------- | ------------------------------------------- |
| `/frewards`                | Abre el menú de recompensas                 |
| `/frewardsreload`          | Recarga todos los archivos de configuración |
| `/frewardsreset <jugador>` | Reinicia todos los cooldowns de un jugador  |

---

## ⛨ Permisos

| Permiso           | Descripción                    |
| ----------------- | ------------------------------ |
| `frewards.use`    | Permite usar `/frewards`       |
| `frewards.reload` | Permite usar `/frewardsreload` |
| `frewards.admin`  | Permite usar `/frewardsreset`  |

---

## 🔄 Cooldowns

Cada recompensa puede tener su propio tiempo de espera (en segundos). Se guarda por jugador.

Ejemplo:

cooldown: 86400 # 24 horas

## ⚛ Ejemplo de configuración

```yaml
rewards:
  daily:
    name: "Diaria"
    display-name: "&fRecompensa &aDiaria"
    permission: "frewards.daily"
    cooldown: 86400
    slot: 10
    material: "CHEST"
    commands:
      - "give %player% diamond 1"
    lore:
      disponible:
        - "&7Haz clic para reclamar"
      progreso:
        - "&7Siguiente en: %cooldown%"
      noperm:
        - "&cNo tienes permiso"
```

---

## 🧪 Integraciones

Este plugin utiliza:

* [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
* Kyori Adventure API para soporte de colores hexadecimales

---

## 🚀 Instalación

1. Descarga el .jar y colócalo en `/plugins`
2. Reinicia el servidor o usa `/reload`
3. Configura `rewardsgui.yml` a tu gusto

---

## 🎨 Créditos

Desarrollado con ❤ por **xLeandroMC**

✔ ¡Compatible con versiones futuras!
✔ Apoyo y sugerencias en [GitHub Issues](https://github.com/xLeandroMC/FRewards/issues)

