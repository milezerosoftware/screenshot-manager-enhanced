---
name: 📥 Pull Request
about: Submit a code change for review and merging.
title: 'feat: '
labels: 'PR'
assignees: ''
---

## 🔗 Related Issue

(Link the issue that this pull request closes or relates to. This provides context for the reviewer.)

Closes #
Relates to #

---

## 🌟 What's in this Pull Request?

(Provide a clear, high-level summary of the changes and what problem this PR solves. This helps reviewers understand the context immediately.)

**Example (DO NOT INCLUDE IN YOUR RESPONSE):** This PR introduces the new user profile photo upload feature. It adds a new API endpoint, updates the user model, and implements the UI for file selection and preview.

---

## 💻 Technical Details

(List the specific, low-level changes you made. This section is for the developer-to-developer details of the code changes.)

- **Added:** `path/to/new_file.js` - A new component for image cropping.
- **Modified:** `path/to/existing_file.js` - Refactored the authentication service to use async/await.
- **Removed:** `path/to/old_file.js` - Deleted the legacy image upload logic.

---

## ✅ Checklist for Review

(This checklist ensures all necessary steps have been taken before merging and provides a quick way for the reviewer to verify the work. Leave checkboxes as [ ] unless the task is completed.)

- [ ] **Code Quality:** My code adheres to the project's coding standards and style guide.
- [ ] **Tests:** New or updated unit/integration tests have been added to cover the changes.
- [ ] **Functionality:** I have manually tested the changes and they work as expected.
- [ ] **Documentation:** I have updated relevant documentation (e.g., API docs, README) if my changes require it.
- [ ] **Dependencies:** I have checked for and updated any necessary dependencies.
- [ ] **No Regression:** My changes do not break existing functionality.

---

## 🧪 How to Test These Changes

(Provide explicit, step-by-step instructions for the reviewer to pull, run, and test your code locally.)

1.  Pull this branch: `git checkout <branch-name>`
2.  Install dependencies: `npm install`
3.  Run the application: `npm start`
4.  Navigate to `https://onepage.io/`
5.  Perform the following actions to test the changes:
    - (List the steps here, referencing the **"Testing Plan"** from the original task/feature issue.)

### ✨ Test Environment

(add_url_here)

#### Reason for no test environment:

- [ ] Under feature flag
- [ ] Going into feature branch
- [ ] Not user facing
- [ ] Other, please explain:

---

## 📸 Screenshots & Recordings

(If there are any visual changes, provide screenshots or a short video to demonstrate the new functionality or fixes. This is a crucial step for visual confirmation.)

<!-- LLM: If you cannot provide a direct attachment, state that attachments must be manually uploaded to the GitHub issue after creation. -->

(Drag and drop images here or provide a link after issue creation.)

---

## 📝 Additional Notes

(Any other information that could be helpful, such as known issues, potential side effects, or a summary of a design conversation.)

---
