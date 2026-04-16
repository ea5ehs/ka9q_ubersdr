# Android Client Bootstrap Validation

Android client bootstrap validated:

1. generate `user_session_id`
2. `POST /connection`
3. if `allowed=true` -> `GET /api/description`
4. UI updates with backend `default_frequency` and `default_mode`

Validated result:
- backend returned `default_frequency=7062000`
- backend returned `default_mode=lsb`

No WebSockets opened yet at this stage.
