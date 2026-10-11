import os
import random
import sqlite3
import unicodedata
from contextlib import contextmanager
from pathlib import Path


DB_PATH = Path(
    os.getenv("STUDENT_DB_PATH", "/data/students.db")
)

PUBLIC_COLUMNS = (
    "mssv, full_name, age, gender, phone, address, "
    "student_email, personal_email, enrollment_year"
)


@contextmanager
def connection():
    db = sqlite3.connect(DB_PATH, timeout=10)
    db.row_factory = sqlite3.Row

    try:
        with db:
            yield db
    finally:
        db.close()


def init_db():
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)

    with connection() as db:
        db.execute("""
            CREATE TABLE IF NOT EXISTS students (
                mssv TEXT PRIMARY KEY,
                login_email TEXT NOT NULL,
                full_name TEXT NOT NULL,
                age INTEGER NOT NULL,
                gender TEXT NOT NULL,
                phone TEXT NOT NULL,
                address TEXT NOT NULL,
                student_email TEXT NOT NULL,
                personal_email TEXT,
                enrollment_year TEXT NOT NULL CHECK (
                    enrollment_year IN (
                        '2016','2017','2018','2019',
                        '2020','2021','2022','2023'
                    )
                )
            )
        """)


def read_profile(db, mssv):
    row = db.execute(
        f"SELECT {PUBLIC_COLUMNS} FROM students WHERE mssv = ?",
        (mssv,),
    ).fetchone()

    return dict(row) if row else None


def ensure_student(login_email, full_name):
    mssv = login_email.split("@", 1)[0]

    with connection() as db:
        db.execute("""
            INSERT INTO students (
                mssv, login_email, full_name, age,
                gender, phone, address, student_email,
                personal_email, enrollment_year
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(mssv) DO NOTHING
        """, (
            mssv,
            login_email,
            full_name,
            20,
            "Khác",
            "0900000000",
            "Địa chỉ demo",
            login_email,
            None,
            str(random.randint(2016, 2023)),
        ))

        return read_profile(db, mssv)


def get_student(mssv):
    with connection() as db:
        return read_profile(db, mssv)


def update_student(mssv, data):
    with connection() as db:
        result = db.execute("""
            UPDATE students SET
                full_name = ?,
                age = ?,
                gender = ?,
                phone = ?,
                address = ?,
                student_email = ?,
                personal_email = ?
            WHERE mssv = ?
        """, (
            data["full_name"],
            data["age"],
            data["gender"],
            data["phone"],
            data["address"],
            data["student_email"],
            data["personal_email"],
            mssv,
        ))

        return (
            read_profile(db, mssv)
            if result.rowcount
            else None
        )


def normalize(text):
    text = unicodedata.normalize(
        "NFD",
        text.casefold().replace("đ", "d"),
    )

    return "".join(
        char
        for char in text
        if not unicodedata.combining(char)
    )


def list_students(q=""):
    with connection() as db:
        rows = db.execute("""
            SELECT mssv, full_name
            FROM students
            ORDER BY full_name, mssv
        """).fetchall()

    term = normalize(q.strip())

    return [
        dict(row)
        for row in rows
        if (
            term in normalize(row["full_name"])
            or term in normalize(row["mssv"])
        )
    ]