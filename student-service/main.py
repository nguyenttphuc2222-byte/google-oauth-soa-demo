import hmac
import os
from contextlib import asynccontextmanager

from fastapi import (
    Depends,
    FastAPI,
    Header,
    HTTPException,
    Security,
)
from fastapi.security import APIKeyHeader

import database
from schemas import (
    Registration,
    StudentDetails,
    StudentSummary,
    StudentUpdate,
)


SERVICE_KEY = os.environ["STUDENT_SERVICE_KEY"]

if len(SERVICE_KEY) < 32:
    raise RuntimeError(
        "STUDENT_SERVICE_KEY must contain at least 32 characters"
    )

key_header = APIKeyHeader(
    name="X-Service-Key",
    auto_error=False,
)


@asynccontextmanager
async def lifespan(app):
    database.init_db()
    yield


app = FastAPI(
    title="TDTU Student Service",
    lifespan=lifespan,
)


def current_user(
    key: str | None = Security(key_header),
    x_login_email: str | None = Header(default=None),
    x_user_role: str | None = Header(default=None),
):
    if not key or not hmac.compare_digest(
        key.encode(),
        SERVICE_KEY.encode(),
    ):
        raise HTTPException(
            401,
            "Invalid service key",
            headers={"WWW-Authenticate": "APIKey"},
        )

    email = (x_login_email or "").strip().lower()
    local, separator, domain = email.partition("@")

    expected = {
        "STUDENT": "student.tdtu.edu.vn",
        "LECTURER": "tdtu.edu.vn",
    }

    if (
        not local
        or not separator
        or domain != expected.get(x_user_role)
    ):
        raise HTTPException(
            403,
            "Invalid TDTU identity or role",
        )

    return {
        "role": x_user_role,
        "login_email": email,
        "mssv": local,
    }


def require_role(user, role):
    if user["role"] != role:
        raise HTTPException(
            403,
            "You do not have permission",
        )


def found(profile):
    if profile is None:
        raise HTTPException(404, "Student not found")

    return profile


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post(
    "/students/me/ensure",
    response_model=StudentDetails,
)
def ensure_me(
    body: Registration,
    user=Depends(current_user),
):
    require_role(user, "STUDENT")

    return database.ensure_student(
        user["login_email"],
        body.full_name,
    )


@app.get(
    "/students/me",
    response_model=StudentDetails,
)
def get_me(user=Depends(current_user)):
    require_role(user, "STUDENT")

    return found(
        database.get_student(user["mssv"])
    )


@app.put(
    "/students/me",
    response_model=StudentDetails,
)
def update_me(
    body: StudentUpdate,
    user=Depends(current_user),
):
    require_role(user, "STUDENT")

    return found(
        database.update_student(
            user["mssv"],
            body.model_dump(),
        )
    )


@app.get(
    "/students",
    response_model=list[StudentSummary],
)
def search_students(
    q: str = "",
    user=Depends(current_user),
):
    require_role(user, "LECTURER")

    return database.list_students(q)


@app.get(
    "/students/{mssv}",
    response_model=StudentDetails,
)
def student_details(
    mssv: str,
    user=Depends(current_user),
):
    require_role(user, "LECTURER")

    return found(
        database.get_student(mssv)
    )