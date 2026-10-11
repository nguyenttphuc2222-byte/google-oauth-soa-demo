package com.example.googleoauthspring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TdtuRolePolicyTests {

    @Test
    void recognizesStudentIdsContainingLetters() {
        assertEquals("STUDENT", TdtuRolePolicy.roleFor(
                "523h0080@student.tdtu.edu.vn", true, "tdtu.edu.vn"));
    }

    @Test
    void recognizesLecturersAndSecondaryHostedDomain() {
        assertEquals("LECTURER", TdtuRolePolicy.roleFor(
                "giangvien@tdtu.edu.vn", true, "tdtu.edu.vn"));
        assertEquals("STUDENT", TdtuRolePolicy.roleFor(
                "523h0080@student.tdtu.edu.vn", true, "student.tdtu.edu.vn"));
    }

    @Test
    void rejectsPersonalAndLookalikeDomains() {
        for (String email : new String[] {
                "student@gmail.com", "person@tdtu.edu.vn.evil.example",
                "person@faketdtu.edu.vn", "person@student.tdtu.edu.vn.evil.example"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> TdtuRolePolicy.roleFor(email, true, "tdtu.edu.vn"));
        }
    }

    @Test
    void rejectsUnverifiedOrNonWorkspaceIdentities() {
        assertThrows(IllegalArgumentException.class, () -> TdtuRolePolicy.roleFor(
                "person@tdtu.edu.vn", false, "tdtu.edu.vn"));
        assertThrows(IllegalArgumentException.class, () -> TdtuRolePolicy.roleFor(
                "person@tdtu.edu.vn", true, null));
        assertThrows(IllegalArgumentException.class, () -> TdtuRolePolicy.roleFor(
                "person@tdtu.edu.vn", true, "other.example"));
    }
}
