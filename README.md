# AI Assistant & Chat App for Android

A modern, full-featured AI Assistant Android application built with Kotlin, Jetpack Compose, Material 3, and Google Gemini API.

## Features

- **Multimodal AI**: Chat with Gemini 2.5 Flash, Gemini 2.0 Flash, and Gemini 2.5 Flash Lite supporting images, PDFs, documents, and plain text.
- **Search Grounding**: Integrated Google Search grounding for real-time directions, routes, upcoming events, and current information.
- **Real-Time Streaming**: Smooth SSE (Server-Sent Events) response streaming with Markdown rendering, code highlighting, and LaTeX math formatting.
- **Local Persistence**: Full offline history storage with Room database.
- **Image Generation**: Built-in image generation capabilities with Pollinations / FLUX integration.
- **Voice Input & Text-to-Speech**: Speech recognition and response read-aloud support.

## Getting Started

### Prerequisites

- **Android Studio** (Ladybug | 2024.2.1 or newer recommended)
- **JDK 17** or **JDK 21**
- **Android SDK Platform 36**

### Setup & Configuration

1. Clone or open the repository in Android Studio:
   ```bash
   git clone <your-github-repo-url>
   ```

2. Configure your Gemini API Key:
   - Copy `.env.example` to `.env`:
     ```bash
     cp .env.example .env
     ```
   - Add your Google AI Studio API key in `.env`:
     ```env
     GEMINI_API_KEY=AIzaSyYourActualKeyHere
     ```
   - Alternatively, you can enter your API key directly inside the app's **Settings** screen at runtime.

3. Build and Run:
   - Open the project in Android Studio and let Gradle sync.
   - Run on an Android device or emulator (Android 7.0+ / API 24+).

## Tech Stack

- **UI**: Jetpack Compose & Material 3 (Material Design 3)
- **Architecture**: MVVM with Kotlin Coroutines & Flow
- **Database**: Android Jetpack Room
- **Networking**: OkHttp, Retrofit, Moshi
- **Image Loading**: Coil
- **AI Integration**: Google Gemini REST API with SSE Streaming & Grounding
