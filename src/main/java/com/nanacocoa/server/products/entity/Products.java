package com.nanacocoa.server.products.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Products {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(nullable = false)
	private Long id;

	@Column(nullable = false, unique = true, length = 255)
	private String name;

	@Column(nullable = false)
	private Long price;

	@Column(nullable = false, length = 500)
	private String summary;

	@Column(name = "detail_title", nullable = false, length = 255)
	private String detailTitle;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String description;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Builder
	public Products(String name, Long price, String summary, String detailTitle, String description, String imageUrl) {
		this.name = name;
		this.price = price;
		this.summary = summary;
		this.detailTitle = detailTitle;
		this.description = description;
		this.imageUrl = imageUrl;
	}

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
}
