package com.example.googleoauthspring;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record StudentProfile(
        String mssv,
        String full_name,
        int age,
        String gender,
        String phone,
        String address,
        String student_email,
        String personal_email,
        String enrollment_year) {

    private static final List<String> EDITABLE = List.of(
            "full_name", "age", "gender", "phone", "address",
            "student_email", "personal_email");

    public record Summary(String mssv, String full_name) {}

    public Map<String, Object> editableFields() {
        var fields = new LinkedHashMap<String, Object>();

        fields.put("full_name", full_name);
        fields.put("age", age);
        fields.put("gender", gender);
        fields.put("phone", phone);
        fields.put("address", address);
        fields.put("student_email", student_email);
        fields.put("personal_email", personal_email);

        return fields;
    }

    public static Map<String, Object> updatePayload(
            Map<String, String> form) {

        if (form.keySet().stream().anyMatch(
                key -> !EDITABLE.contains(key)
                        && !"_csrf".equals(key))) {

            throw new IllegalArgumentException(
                    "Yêu cầu chứa trường không được phép chỉnh sửa.");
        }

        var payload = new LinkedHashMap<String, Object>();

        for (String field : EDITABLE) {
            String value = form.get(field);
            payload.put(field, value == null ? null : value.trim());
        }

        try {
            payload.put(
                    "age",
                    Integer.parseInt(
                            form.getOrDefault("age", "").trim()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Tuổi phải là số nguyên.");
        }

        String personal = (String) payload.get("personal_email");

        payload.put(
                "personal_email",
                personal == null || personal.isBlank()
                        ? null
                        : personal);

        return payload;
    }
}