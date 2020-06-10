package com.bookstore.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PlacedOrderDetail {
	private int invoiceNumber;
	private String authorName;
	private String bookCode;
	private String bookDetails;
	private int bookId;
	private String bookImage;
	private String bookName;
	private double price;
	private int quantity;
	private int userId;
	private String createdTime;
}
