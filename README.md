# HealthComp 🏋️‍♂️📊

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?&style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=android&logoColor=white)
![Room Database](https://img.shields.io/badge/Room_DB-SQLite-blue?style=for-the-badge)

**HealthComp** is a modern, offline-first Android application developed to track macronutrients and workout consistency natively on the user's device. 

Built to eliminate the bloat, network latency, and privacy concerns of mainstream fitness apps, HealthComp provides a lightning-fast, highly personalized tracking experience using **Jetpack Compose** and **Room Persistence Library**.

---

## ✨ Key Features

* **🔌 100% Offline-First:** Zero network latency. All meals and workouts are stored locally on the device using SQLite/Room.
* **⚡ Rapid Macro Logging:** Manually log custom meals without searching through bloated external APIs. Automatically calculates total caloric intake using the Atwater general factor system.
* **📅 Dynamic Workout Calendar:** Create and edit a weekly split (`DailyPlan`). Add specific target muscles and exercises for each day, which dynamically sync with the home dashboard.
* **🏆 Consistency Gamification:** Earn "Consistency Points" for marking daily workouts as complete, utilizing an immediate psychological reward loop to build healthy habits.
* **🕰️ Historical Diary:** A dedicated "My Meals" tab using Kotlin `Flow` combinations to instantly filter and display macronutrient totals for any previous date.

---

## 🛠️ Tech Stack & Architecture

HealthComp strictly enforces the **Model-View-ViewModel (MVVM)** architectural pattern alongside **Unidirectional Data Flow (UDF)**.

* **Language:** [Kotlin](https://kotlinlang.org/)
* **UI Toolkit:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
* **Local Database:** [Room](https://developer.android.com/training/data-storage/room) (SQLite abstraction)
* **Asynchronous Execution:** [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & [Flow](https://kotlinlang.org/docs/flow.html)
* **Navigation:** Navigation Compose
* **Architecture:** MVVM (Model-View-ViewModel)

---

## 📸 Screenshots

*(Replace these placeholder links with actual screenshots of your app once uploaded!)*

|<img src="link_to_dashboard_image" width="200">|<img src="link_to_mymeals_image" width="200">|<img src="link_to_calendar_image" width="200">|<img src="link_to_social_image" width="200">|
|:---:|:---:|:---:|:---:|
| **Dashboard** | **My Meals (Diary)** | **Weekly Calendar** | **Leaderboard** |

---

## 🚀 Getting Started

### Prerequisites
* [Android Studio](https://developer.android.com/studio) (Latest stable version recommended)
* JDK 17+
* Android device or emulator running API Level 24 (Android 7.0) or higher.

### Installation
1. Clone the repository:
   ```bash
   git clone [https://github.com/deshpandedarsh77-sys/HealthComp.git](https://github.com/deshpandedarsh77-sys/HealthComp.git)
