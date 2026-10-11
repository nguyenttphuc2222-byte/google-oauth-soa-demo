from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


EMAIL_PATTERN = r"^[^\s@]+@[^\s@]+\.[^\s@]+$"


class Registration(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        str_strip_whitespace=True,
    )

    full_name: str = Field(
        default="Sinh viên Demo",
        min_length=1,
        max_length=100,
    )


class StudentUpdate(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        str_strip_whitespace=True,
    )

    full_name: str = Field(min_length=1, max_length=100)
    age: int = Field(ge=0, le=120)
    gender: Literal["Nam", "Nữ", "Khác"]
    phone: str = Field(min_length=1, max_length=20)
    address: str = Field(min_length=1, max_length=255)

    student_email: str = Field(
        max_length=254,
        pattern=EMAIL_PATTERN,
    )

    personal_email: str | None = Field(
        default=None,
        max_length=254,
        pattern=EMAIL_PATTERN,
    )


class StudentDetails(StudentUpdate):
    mssv: str

    enrollment_year: str = Field(
        pattern=r"^20(1[6-9]|2[0-3])$",
    )


class StudentSummary(BaseModel):
    mssv: str
    full_name: str