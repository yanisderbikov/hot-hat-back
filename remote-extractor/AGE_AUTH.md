# Optional verified-age YouTube access (SCRUM-58)

This is **not** a generic Google sign-in on the HOT-HAT website. Official
YouTube age-restricted videos cannot generally be played inside third-party
embedded players, even if an adult is logged in. The **private** Singapore
extractor can optionally use an adult account's existing YouTube session
to request a clean 10-second MP4 for a genuinely age-restricted video.

Required variable on **hot-hat-youtube-clip-source only**:
`HOT_HAT_YOUTUBE_COOKIES_B64`. Leave it unset by default.

The variable is the **base64 encoding of a Netscape cookies.txt file**.
Its contents are session credentials, not an API key or harmless age flag.
The user must configure it **directly as a protected Railway service secret**,
not paste it into any chat, GitHub commit, website input, or logs. Use a
dedicated, adult, age-verified YouTube session instead of a primary Google
account when possible, and revoke that session if compromised. Authentication
is attempted only when the anonymous extractor actually reports an age gate.

The service decodes it only inside the private process, allows only
youtube.com/google.com cookie domains, places the file in a per-request
0700 temporary directory with file mode 0600, deletes it after extraction,
never returns credentials to frontend or main HOT-HAT backend, and never
prints cookie values. No public endpoint is provided on the extractor.

The account's age verification is honored only if YouTube accepts its
session cookies from Railway. Invalid/expired cookies, IP challenges,
PO-token requirements, or other YouTube restrictions can still cause
HTTP 422; this feature is **optional and not a guarantee**. It does not
attempt to evade age verification for an unverified account.
