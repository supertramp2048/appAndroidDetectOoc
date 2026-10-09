package com.socialapp.feature.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AuthViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AuthViewModel viewModel;

    @Before
    public void setUp() {
        viewModel = new AuthViewModel(null);
    }

    @Test
    public void testValidateLoginEmptyEmail() {
        String result = viewModel.validateLogin("", "password123");
        assertNotNull(result);
        assertEquals("Vui lòng nhập địa chỉ email.", result);
    }

    @Test
    public void testValidateLoginInvalidEmailFormat() {
        String result = viewModel.validateLogin("invalid-email", "password123");
        assertNotNull(result);
        assertEquals("Địa chỉ email không hợp lệ.", result);
    }

    @Test
    public void testValidateLoginEmptyPassword() {
        String result = viewModel.validateLogin("user@example.com", "");
        assertNotNull(result);
        assertEquals("Vui lòng nhập mật khẩu.", result);
    }

    @Test
    public void testValidateLoginSuccess() {
        String result = viewModel.validateLogin("user@example.com", "password123");
        assertNull(result);
    }

    @Test
    public void testValidateRegisterNameTooLong() {
        String longName = "A".repeat(51);
        String result = viewModel.validateRegister(longName, "user@example.com", "password123", "password123");
        assertNotNull(result);
        assertEquals("Họ và tên không được vượt quá 50 ký tự.", result);
    }

    @Test
    public void testValidateRegisterPasswordTooShort() {
        String result = viewModel.validateRegister("Nguyễn Văn A", "user@example.com", "12345", "12345");
        assertNotNull(result);
        assertEquals("Mật khẩu phải chứa ít nhất 8 ký tự.", result);
    }

    @Test
    public void testValidateRegisterPasswordMismatch() {
        String result = viewModel.validateRegister("Nguyễn Văn A", "user@example.com", "password123", "password456");
        assertNotNull(result);
        assertEquals("Mật khẩu xác nhận không khớp.", result);
    }

    @Test
    public void testValidateRegisterSuccess() {
        String result = viewModel.validateRegister("Nguyễn Văn A", "user@example.com", "password123", "password123");
        assertNull(result);
    }
}
