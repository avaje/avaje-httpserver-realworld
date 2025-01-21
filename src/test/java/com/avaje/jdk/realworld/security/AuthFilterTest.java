package com.avaje.jdk.realworld.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.avaje.jdk.realworld.web.service.TokenService;
import io.avaje.jex.Context;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthFilterTest {

  private final TokenService mockDb = mock(TokenService.class);

  Context context = mock(Context.class);
  JWTAuthFilter filter = new JWTAuthFilter(mockDb);

  @BeforeEach
  void setup() {
    doReturn(Set.of()).when(context).routeRoles();
  }

  @Test
  void testAnyone() throws Exception {
    doReturn(Set.of(AppRole.ANYONE)).when(context).routeRoles();
    filter.authFilter(
        context,
        () -> {
          // filter passed without issue
          assertTrue(true);
        });
  }
}
