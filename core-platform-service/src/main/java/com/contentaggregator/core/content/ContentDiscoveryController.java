package com.contentaggregator.core.content;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content")
public class ContentDiscoveryController {

  private final ContentDiscoveryService discoveryService;

  public ContentDiscoveryController(ContentDiscoveryService discoveryService) {
    this.discoveryService = discoveryService;
  }

  @GetMapping("/search")
  public List<SearchResultItem> search(
      @RequestParam("q") String query,
      @RequestParam(value = "limit", defaultValue = "20") int limit) {
    return discoveryService.search(query, limit);
  }
}
