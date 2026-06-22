---
name: project-campusgo
description: Overview of the CampusGO Android project — what it is, tech stack, architecture, and Firebase data layout
metadata:
  type: project
---

CampusGO is a gig-economy Android app where students post tasks (food pickup, printing, parcels, errands) and other students ("runners") earn money by completing them. Package ID: `com.CampusGO.app`.

**Tech stack**
- Language: Kotlin
- UI: XML layouts with ViewBinding; Material Design components
- Backend: Firebase Auth (email/password) + Firebase Realtime Database (no Firestore)
- Min SDK 31, Target SDK 36

**Firebase data structure**
- `/users/{uid}` — User model: uid, fullName, email, rating, totalReviews, createdAt
- `/userStats/{uid}` — completedTasks, postedTasks, acceptedTasks, rating, totalReviews
- `/tasks/{taskId}` — Task model (see below)
- `/chats/{chatId}` — Chat per (task × runner): id, taskId, taskTitle, taskNumber, posterId, posterName, runnerId, runnerName, lastMessage, lastMessageTime, finalPrice
- `/messages/{chatId}/{msgId}` — Message: id, senderId, senderName, content, type, priceAmount, priceStatus, createdAt
- `/reports/{pushId}` — issue reports from TaskTrackingActivity

**Task model fields**: id, taskNumber (CG-XXXX), title, category (FOOD/PRINT/PARCEL/ERRAND/OTHER), pickup, dropoff, description, price, isNegotiable, isEmergency, status, posterId, posterName, posterRating, posterReviews, runnerId, runnerName, agreedPrice, createdAt

**Task statuses**: OPEN → ACCEPTED → ON_THE_WAY → DELIVERED → COMPLETED (or CANCELLED)

**Chat ID convention**: `{taskId}_{runnerId}` — so there is one chat per (task, runner) pair

**Activity/Fragment map**
- SplashActivity → checks auth → LoginActivity or MainActivity
- LoginActivity / RegisterActivity — Firebase Auth sign-in/register
- MainActivity — bottom nav shell hosting 4 fragments: Feed, Tasks, ChatList, Profile
- FeedFragment — shows all OPEN tasks not posted by current user; filter chips (Emergency, Highest, Newest)
- TasksFragment — user's own tasks split into Posted / Accepted / Completed tabs
- ChatListFragment — all chats the user is part of
- ProfileFragment — name, email, rating, stats; sign out
- CreateTaskActivity — post a new task; has keyword content moderation (bannedKeywords set)
- TaskDetailActivity — view task; runner can Accept or Negotiate (open chat); poster can Cancel
- ChatActivity — real-time chat per task×runner; supports text messages and price offer/accept flow
- ConfirmAcceptActivity — final acceptance screen showing original vs agreed price
- TaskTrackingActivity — timeline view (Posted→Accepted→OnTheWay→Delivered→Completed); runner advances status, poster confirms receipt; rating dialog on completion; report dialog

**Edge-to-edge / insets**: targetSdk 36 enables edge-to-edge automatically on Android 15+. All screens use `InsetsHelper.applyTopInsetPadding()` (see `InsetsHelper.kt`) on their top header/toolbar container. All toolbar containers use `android:layout_height="wrap_content"` + `android:minHeight="56dp"` (NOT a fixed 56dp) so they expand to hold both the inset padding and the toolbar content. FeedFragment uses `android:fitsSystemWindows="true"` on CoordinatorLayout + AppBarLayout instead. Login/Register apply insets to the root ScrollView (hardcoded `layout_marginTop` replaced with 16dp).

**Chat creation rule**: Only the runner creates the `/chats/{chatId}` document. `ChatActivity.ensureChatExists(task)` is called immediately after `loadTask()` resolves — this covers all entry points (FeedFragment chat button, TaskDetailActivity negotiate, TaskTrackingActivity chat button). If current user is the poster, `ensureChatExists` is a no-op. `TaskDetailActivity.ensureChatExists()` also creates the doc when the runner clicks Negotiate (belt-and-suspenders). IMPORTANT: this method was lost in a linter revert and had to be restored. If lost again, the symptom is: messages send but chat is invisible in ChatListFragment for both users, because partial doc has no posterId/runnerId.

**Why:** Understand the full app to assist with development effectively.
**How to apply:** Use this as the primary reference for architecture decisions, feature additions, and debugging.
