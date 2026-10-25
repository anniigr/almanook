package app.almanook.identity.web;

import app.almanook.identity.AlmanookPrincipal;
import app.almanook.identity.Theme;
import app.almanook.identity.UserAccountService;
import app.almanook.identity.UserAccountService.Me;
import app.almanook.identity.UserAccountService.Preferences;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Current user")
class MeController {

    record PreferencesRequest(
            @NotNull Theme theme,

            @NotBlank @Size(max = 16) @Pattern(regexp = "[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*")
            String language,

            @NotBlank @Size(max = 64) String timezone) {}

    private final UserAccountService users;

    MeController(UserAccountService users) {
        this.users = users;
    }

    @GetMapping
    Me me(@AuthenticationPrincipal AlmanookPrincipal principal) {
        return users.me(principal.userId());
    }

    @PutMapping("/preferences")
    Preferences updatePreferences(
            @AuthenticationPrincipal AlmanookPrincipal principal, @Valid @RequestBody PreferencesRequest body) {
        return users.updatePreferences(principal.userId(), body.theme(), body.language(), body.timezone());
    }
}
