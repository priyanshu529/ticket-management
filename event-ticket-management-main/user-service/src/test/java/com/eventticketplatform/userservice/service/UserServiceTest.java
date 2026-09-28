package com.eventticketplatform.userservice.service;

import com.eventticketplatform.userservice.dto.UserLoginDto;
import com.eventticketplatform.userservice.dto.UserRegistrationDto;
import com.eventticketplatform.userservice.dto.UserResponseDto;
import com.eventticketplatform.userservice.entity.Role;
import com.eventticketplatform.userservice.entity.User;
import com.eventticketplatform.userservice.exception.ResourceNotFoundException;
import com.eventticketplatform.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    private User buildUser(Long id, String name, String email, String rawPassword, Role role) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setPassword(encoder.encode(rawPassword));
        user.setRole(role);
        return user;
    }

    private UserRegistrationDto registrationDto(String name, String email, String password, Role role) {
        UserRegistrationDto dto = new UserRegistrationDto();
        dto.setName(name);
        dto.setEmail(email);
        dto.setPassword(password);
        dto.setRole(role);
        return dto;
    }

    private UserLoginDto loginDto(String email, String password) {
        UserLoginDto dto = new UserLoginDto();
        dto.setEmail(email);
        dto.setPassword(password);
        return dto;
    }

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("should register a new USER and return the saved user as a response DTO")
        void register_newUser_success() {
            UserRegistrationDto dto = registrationDto("Alice", "alice@example.com", "secret", Role.USER);
            when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
            User savedUser = new User();
            savedUser.setId(1L); savedUser.setName("Alice"); savedUser.setEmail("alice@example.com");
            savedUser.setPassword("$2a$10$hashed"); savedUser.setRole(Role.USER);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            UserResponseDto result = userService.register(dto);
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("Alice");
            assertThat(result.getEmail()).isEqualTo("alice@example.com");
            assertThat(result.getRole()).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("should register an ORGANIZER when role is ORGANIZER")
        void register_organizer_setsOrganizerRole() {
            UserRegistrationDto dto = registrationDto("Bob", "bob@example.com", "secret", Role.ORGANIZER);
            when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
            User savedUser = new User();
            savedUser.setId(2L); savedUser.setName("Bob"); savedUser.setEmail("bob@example.com");
            savedUser.setPassword("$2a$10$hashed"); savedUser.setRole(Role.ORGANIZER);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            UserResponseDto result = userService.register(dto);
            assertThat(result.getRole()).isEqualTo(Role.ORGANIZER);
        }

        @Test
        @DisplayName("should default to USER role when ADMIN is requested (security guard)")
        void register_adminRequested_defaultsToUser() {
            UserRegistrationDto dto = registrationDto("Eve", "eve@example.com", "secret", Role.ADMIN);
            when(userRepository.existsByEmail("eve@example.com")).thenReturn(false);
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            User savedUser = new User();
            savedUser.setId(3L); savedUser.setName("Eve"); savedUser.setEmail("eve@example.com");
            savedUser.setPassword("$2a$10$hashed"); savedUser.setRole(Role.USER);
            when(userRepository.save(captor.capture())).thenReturn(savedUser);
            UserResponseDto result = userService.register(dto);
            assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
            assertThat(result.getRole()).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when email is already registered")
        void register_duplicateEmail_throwsException() {
            UserRegistrationDto dto = registrationDto("Alice", "alice@example.com", "secret", Role.USER);
            when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);
            assertThatThrownBy(() -> userService.register(dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("alice@example.com");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should store a BCrypt-hashed password, never the plain-text one")
        void register_passwordIsHashed() {
            String rawPassword = "myPlainPassword";
            UserRegistrationDto dto = registrationDto("Carol", "carol@example.com", rawPassword, Role.USER);
            when(userRepository.existsByEmail("carol@example.com")).thenReturn(false);
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            User savedUser = new User();
            savedUser.setId(4L); savedUser.setName("Carol"); savedUser.setEmail("carol@example.com");
            savedUser.setPassword("$2a$10$hashed"); savedUser.setRole(Role.USER);
            when(userRepository.save(captor.capture())).thenReturn(savedUser);
            userService.register(dto);
            String stored = captor.getValue().getPassword();
            assertThat(stored).isNotEqualTo(rawPassword);
            assertThat(stored).startsWith("$2");
            assertThat(new BCryptPasswordEncoder().matches(rawPassword, stored)).isTrue();
        }
    }

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("should return user response DTO when credentials are correct")
        void login_correctCredentials_success() {
            String rawPassword = "correctPassword";
            User user = buildUser(10L, "Dave", "dave@example.com", rawPassword, Role.USER);
            when(userRepository.findByEmail("dave@example.com")).thenReturn(Optional.of(user));
            UserResponseDto result = userService.login(loginDto("dave@example.com", rawPassword));
            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getName()).isEqualTo("Dave");
            assertThat(result.getRole()).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when password is wrong")
        void login_wrongPassword_throwsException() {
            User user = buildUser(10L, "Dave", "dave@example.com", "correctPassword", Role.USER);
            when(userRepository.findByEmail("dave@example.com")).thenReturn(Optional.of(user));
            assertThatThrownBy(() -> userService.login(loginDto("dave@example.com", "wrongPassword")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid email or password");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when email does not exist")
        void login_emailNotFound_throwsException() {
            when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.login(loginDto("nobody@example.com", "any")))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("nobody@example.com");
        }

        @Test
        @DisplayName("should auto-upgrade a legacy plain-text password to BCrypt on successful login")
        void login_legacyPlainTextPassword_isUpgradedToBcrypt() {
            String plainPassword = "legacyPassword";
            User legacyUser = new User();
            legacyUser.setId(20L); legacyUser.setName("Legacy");
            legacyUser.setEmail("legacy@example.com");
            legacyUser.setPassword(plainPassword);
            legacyUser.setRole(Role.USER);
            when(userRepository.findByEmail("legacy@example.com")).thenReturn(Optional.of(legacyUser));
            when(userRepository.save(any(User.class))).thenReturn(legacyUser);
            UserResponseDto result = userService.login(loginDto("legacy@example.com", plainPassword));
            assertThat(result.getId()).isEqualTo(20L);
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            String upgraded = captor.getValue().getPassword();
            assertThat(upgraded).startsWith("$2");
            assertThat(new BCryptPasswordEncoder().matches(plainPassword, upgraded)).isTrue();
        }
    }

    @Nested
    @DisplayName("getUserById()")
    class GetUserById {

        @Test
        @DisplayName("should return user response DTO when user exists")
        void getUserById_exists_returnsDto() {
            User user = buildUser(5L, "Frank", "frank@example.com", "pass", Role.ORGANIZER);
            when(userRepository.findById(5L)).thenReturn(Optional.of(user));
            UserResponseDto result = userService.getUserById(5L);
            assertThat(result.getId()).isEqualTo(5L);
            assertThat(result.getName()).isEqualTo("Frank");
            assertThat(result.getRole()).isEqualTo(Role.ORGANIZER);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when user does not exist")
        void getUserById_notFound_throwsException() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> userService.getUserById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("should never expose the password field in the response DTO")
        void getUserById_responseDto_doesNotContainPassword() {
            User user = buildUser(6L, "Grace", "grace@example.com", "secret", Role.USER);
            when(userRepository.findById(6L)).thenReturn(Optional.of(user));
            UserResponseDto result = userService.getUserById(6L);
            assertThat(result.getId()).isNotNull();
            assertThat(result.getName()).isNotNull();
            assertThat(result.getEmail()).isNotNull();
            assertThat(result.getRole()).isNotNull();
        }
    }
}