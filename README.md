# VoiceNotes
[![Android CI](https://github.com/Akbar02Work/VoiceNotes/actions/workflows/android.yml/badge.svg)](https://github.com/Akbar02Work/VoiceNotes/actions/workflows/android.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-7%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
VoiceNotes is a native Android app that turns short recordings into searchable notes with a
transcription, title, summary, and playable audio. Processing can use Gemini, OpenAI, Groq, or a
fully on-device Russian speech-to-text model.
The project focuses on a polished recording flow, recoverable failure states, secure bring-your-own
key storage, and offline inference that does not treat arbitrary model files as interchangeable.
## Screenshots
<table>  
<tr>    
<th>Cloud setup</th>    
<th>On-device setup</th>  
</tr>  
<tr>    
<td><img src="screenshots/setup-cloud.jpg" alt="Cloud provider setup" width="360"></td>    
<td><img src="screenshots/setup-offline.jpg" alt="On-device model setup" width="360"></td>  
</tr>
</table>
<table>  
<tr>    
<th>Notes list</th>    
<th>Note details and playback</th>    
<th>Settings</th>  
</tr>  
<tr>    
<td><img src="screenshots/notes-list.jpg" alt="Notes list" width="260"></td>    
<td><img src="screenshots/note-details.jpg" alt="Transcribed note details" width="260"></td>    
<td><img src="screenshots/settings.jpg" alt="Appearance and language settings" width="260"></td>  
</tr>
</table>
## Highlights
- One-tap AAC recording with clear recording, processing, draft, failure, and retry states
- AI transcription plus generated titles and summaries
- Gemini, OpenAI, and Groq with API-key validation and compatible model discovery
- Russian offline speech-to-text powered by sherpa-onnx and INT8 Zipformer models
- Resumable model downloads with size checks, SHA-256 verification, and atomic activation
- Persistent Room storage with pinned notes and playable original audio
- API keys stored with Android encrypted preferences and excluded from backup
- English and Russian localization
- Material 3 UI with dynamic color and light, dark, and system themes
## Processing modes
| Mode | Transcription | Title and summary | Network |
| --- | --- | --- | --- |
| Gemini | Multimodal `generateContent` | Gemini model selected by the user | Required |
| OpenAI | Audio transcription endpoint | Chat completions | Required |
| Groq | OpenAI-compatible Whisper | OpenAI-compatible chat | Required |
| On device | sherpa-onnx Zipformer | Extractive local processing | Not required after download |
Cloud mode uses a bring-your-own-key model. VoiceNotes validates the key, discovers compatible
models, and stores the selected transcription and summary models separately.

Built by [Akbar Azizov](https://www.akbar02work.xyz) as a portfolio-focused Android project.

[Read the VoiceNotes case study →](https://www.akbar02work.xyz/projects/voicenotes)
