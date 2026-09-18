# Third-Party Notices & Attribution

This application (**Crank**) incorporates technical concepts, architectural patterns, and media player design inspired by open-source projects.

---

## 1. Metrolist

* **Repository**: [https://github.com/MetrolistGroup/Metrolist](https://github.com/MetrolistGroup/Metrolist)
* **License**: GNU General Public License v3.0 (GPL-3.0)
* **Copyright**: (c) Metrolist Group and contributors

### Adapted Technical Concepts:
* Centralized Media3 ExoPlayer & MediaSessionService architecture
* Room Database schemas for persistent queue, search history, playlists, and playback state
* Audio playback speed, pitch, shuffle, and repeat control patterns
* Synced lyrics presentation and sleep timer mechanisms

*Note: All UI components, branding, styling, color palette (Obsidian & Gold), and package namespaces are original to Crank and distinct from Metrolist.*

---

## 2. Android Jetpack & Media3 Libraries

* **Developer**: Google LLC / Android Open Source Project
* **License**: Apache License 2.0
* **Libraries**:
  * `androidx.media3:media3-exoplayer`
  * `androidx.media3:media3-session`
  * `androidx.room:room-runtime`, `androidx.room:room-ktx`
  * `androidx.compose.material3:material3`
  * `com.google.dagger:hilt-android`

---

## 3. Ktor Client & KotlinX Serialization

* **Developer**: JetBrains s.r.o.
* **License**: Apache License 2.0
* **Libraries**: `io.ktor:ktor-client-android`, `org.jetbrains.kotlinx:kotlinx-serialization-json`

---

## 4. Coil Image Loading

* **Developer**: Coil Contributors
* **License**: Apache License 2.0
* **Library**: `io.coil-kt.coil3:coil-compose`
