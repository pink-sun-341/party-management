CREATE TABLE IF NOT EXISTS members (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    gender TEXT,
    phone TEXT,
    status TEXT NOT NULL DEFAULT '正式党员',
    application_date TEXT,
    activist_date TEXT,
    development_object_date TEXT,
    probationary_date TEXT,
    full_member_date TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS activities (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    type TEXT NOT NULL,
    activity_date TEXT NOT NULL,
    location TEXT,
    participant_count INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS meetings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    meeting_date TEXT NOT NULL,
    location TEXT,
    host TEXT,
    recorder TEXT,
    agenda TEXT,
    discussion TEXT,
    decisions TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
