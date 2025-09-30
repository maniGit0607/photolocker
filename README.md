# PhotoVault Locker

A secure Android application that allows users to import photos from their device gallery into private albums with password protection.

## Features

- **Password Protection**: Secure your photos with a custom password
- **Album Management**: Create and manage multiple photo albums
- **Photo Import**: Import photos from device gallery into private storage
- **Gallery Deletion**: Automatically delete imported photos from device gallery for privacy
- **Full-Screen Viewing**: View photos in full-screen gallery mode with swipe navigation
- **Photo Management**: Delete photos from albums, view photo details

## Key Functionality

1. **Authentication**: Set up a password on first launch, required for all subsequent access
2. **Create Albums**: Tap the + button to create new photo albums
3. **Import Photos**: Select photos from device gallery to import into albums
4. **Automatic Cleanup**: Photos are automatically deleted from device gallery after import
5. **View Photos**: Tap any photo to view in full-screen mode with swipe navigation
6. **Manage Photos**: Long-press photos to select and delete multiple items

## Technical Implementation

- **Architecture**: MVVM pattern with Room database
- **Database**: SQLite with Room ORM for album and photo metadata storage
- **Security**: Encrypted SharedPreferences for password storage
- **File Management**: Custom file storage within app's private directory
- **UI**: Material Design components with modern Android UI patterns

## Permissions Required

- **READ_EXTERNAL_STORAGE**: To access device gallery photos
- **WRITE_EXTERNAL_STORAGE**: To delete photos from gallery (API level ≤ 28)
- **READ_MEDIA_IMAGES**: For accessing images on Android 13+ devices

## Build Requirements

- Android Studio Arctic Fox or later
- Minimum SDK: API 21 (Android 5.0)
- Target SDK: API 34 (Android 14)
- Kotlin support enabled

## Security Features

- Password hashing with SHA-256
- Encrypted storage for sensitive data
- Photos stored in app's private directory
- No backup of sensitive data to cloud services

## Installation

1. Clone the repository
2. Open in Android Studio
3. Build and run on device or emulator
4. Set up your password on first launch
5. Start creating albums and importing photos!

## Note

This app permanently deletes imported photos from your device gallery. Make sure you have backups of important photos before using this app.

