package com.contentaggregator.core.content;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.contentaggregator.annotations.WebMvcUnitTest;

@WebMvcUnitTest
@WebMvcTest(FeedController.class)
class FeedControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FeedService feedService;

  @Test
  @DisplayName("GET /api/v1/feed with valid userId and limit returns 200 and JSON array")
  void shouldReturnFeedSuccessfully() throws Exception {
    UUID userId = UUID.randomUUID();
    FeedItemResponse item =
        new FeedItemResponse(
            UUID.randomUUID(),
            "source-1",
            "ext-1",
            "Clean Architecture Post",
            "https://example.com/clean-arch",
            "Summary",
            LocalDateTime.of(2026, 9, 29, 12, 0));

    given(feedService.getUserFeed(userId, 20)).willReturn(List.of(item));

    mockMvc
        .perform(
            get("/api/v1/feed")
                .param("userId", userId.toString())
                .param("limit", "20")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].title").value("Clean Architecture Post"))
        .andExpect(jsonPath("$[0].url").value("https://example.com/clean-arch"));
  }

  @Test
  @DisplayName("GET /api/v1/feed without required userId returns 400 Bad Request")
  void shouldReturn400WhenUserIdMissing() throws Exception {
    mockMvc
        .perform(get("/api/v1/feed").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /api/v1/feed with malformed UUID returns 400 Bad Request")
  void shouldReturn400WhenUserIdMalformed() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/feed")
                .param("userId", "not-a-valid-uuid")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }
}
