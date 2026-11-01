package com.example.order.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RestController.class)
class RestControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void getMessageReturnsGreeting() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string("Hello from RestController!"));
	}

	@Test
	void postItemAddsItToList() throws Exception {
		mockMvc.perform(post("/order").content("apple"))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/order"))
				.andExpect(status().isOk())
				.andExpect(content().json("[\"apple\"]"));
	}
}
