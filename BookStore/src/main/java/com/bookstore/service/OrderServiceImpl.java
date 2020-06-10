package com.bookstore.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.client.RestTemplate;

import com.auth0.jwt.exceptions.AlgorithmMismatchException;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.bookstore.dao.BookDaoImpl;
import com.bookstore.dao.IOrderDAO;
import com.bookstore.dao.IQuantityDAO;
import com.bookstore.dto.MailResponse;
import com.bookstore.dto.PlacedOrderDetail;
import com.bookstore.entity.Book;
import com.bookstore.entity.Cart;
import com.bookstore.entity.Order;
import com.bookstore.entity.Quantity;
import com.bookstore.entity.UserData;
import com.bookstore.exception.InvalidTokenOrExpiredException;
import com.bookstore.exception.UserDoesNotExistException;
import com.bookstore.response.OrderListResponse;
import com.bookstore.response.OrderResponse;
import com.bookstore.util.JwtTokenUtil;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;

/***************************************************************************************************
 * OrderService Implementation Class By using the object reference of OrderDAO
 * for DataBase Related Support, RestTemplate for comminication between
 * BookStore-Api & BookSrore WebApi, JwtTokenUtil to Generate the Token And
 * parse the token.This Service Class Contains the implementation methods of
 * IBookService Interface methods like Make book Order,delete book Order, Update
 * Book Order,Confirm Order.
 *
 * @author Rupesh Patil
 * @version 2.0
 * @updated 2020-05-06
 * @created 2020-04-15
 * @see {@link IOrderDAO} implementation of all the required DB related
 *      functionality
 * @see {@link restTemplate} will Inject Object for communicate two web-apis.
 * @see {@link JwtTokenUtil} for parse and generate token.
 * @see {@link JavaMailSender} for Send the Mails.
 ******************************************************************************************************/

@Service
@Slf4j
public class OrderServiceImpl implements IOrderservice {

	@Autowired
	IOrderDAO orderDao;
	@Autowired
	BookDaoImpl bookDao;

	@Autowired
	IQuantityDAO quantityDao;

	@Autowired
	RestTemplate restTemplate;
	@Autowired
	private JwtTokenUtil generateToken;
	static UserData userData;
	double finalAmount = 0;
	@Autowired
	private JavaMailSender sender;
	@Autowired
	Configuration config;

	/*********************************************************************
	 * To make book order by the user.
	 * 
	 * @param String token, int id, int quantity
	 * @return ResponseEntity<Object>
	 ********************************************************************/
	@Override
	public ResponseEntity<Object> makeOrder(int id, int quantity, int userId) {
		Book book = bookDao.getBookByBookId(id);
		Order order = new Order();
		order.setBookId(id);
		order.setUserId(userId);
		order.setQuantity(quantity);
		order.setBookName(book.getBookName());
		order.setPrice(book.getPrice());
		order.setTotal(order.getPrice() * order.getQuantity());
		order.setBookImage(book.getBookImage());
		if (orderDao.addOrder(order) > 0) {
			book.setQuantity(book.getQuantity() - 1);
			bookDao.updateBook(book, book.getBookName());
			System.out.println("Added successfully");
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(new OrderResponse(202, "Order Added to cart"));
		}

		return null;
	}

	public ResponseEntity<Object> makeOrderWithToken(int id, int quantity, String token) {
		Book book = bookDao.getBookByBookId(id);
		Order order = new Order();
		order.setBookId(id);
		order.setUserId(generateToken.parseToken(token));
		order.setQuantity(quantity);
		order.setBookName(book.getBookName());
		order.setPrice(book.getPrice());
		order.setTotal(order.getPrice() * order.getQuantity());
		order.setBookImage(book.getBookImage());
		if (orderDao.addOrder(order) > 0) {
			book.setQuantity(book.getQuantity() - 1);
			bookDao.updateBook(book, book.getBookName());
			System.out.println("Added successfully");
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(new OrderResponse(202, "Order Added to cart"));
		}

		return null;
	}

	/*********************************************************************
	 * To get List Of Book from the Order list db table.
	 * 
	 * @param String token
	 * @return ResponseEntity<Object>
	 ********************************************************************/
	@Override
	public ResponseEntity<Object> getCartList(int userId) {
		Optional<List<Order>> orders = null;

		orders = Optional.ofNullable(orderDao.getOrderList(userId));
		if (orders.isPresent()) {
			return ResponseEntity.status(HttpStatus.ACCEPTED)
					.body(new OrderListResponse(202, "total books in cart" + orders.get().size(), orders.get()));
		} else {
			return ResponseEntity.status(HttpStatus.ACCEPTED)
					.body(new OrderResponse(202, "No any Books Added to cart"));
		}
	}

	public ResponseEntity<Object> getCartListWithToken(String token) {
		Optional<List<Order>> orders = null;

		orders = Optional.ofNullable(orderDao.getOrderList(generateToken.parseToken(token)));
		if (orders.isPresent()) {
			return ResponseEntity.status(HttpStatus.ACCEPTED)
					.body(new OrderListResponse(202, "total books in cart" + orders.get().size(), orders.get()));
		} else {
			return ResponseEntity.status(HttpStatus.ACCEPTED)
					.body(new OrderResponse(202, "No any Books Added to cart"));
		}

	}

	/*********************************************************************
	 * To update Quantity of books by the user then user can increse or decrease
	 * Quantity for purchase books.
	 * 
	 * @param String token, Order order
	 * @return ResponseEntity<Object>
	 ********************************************************************/
	@Override
	public ResponseEntity<Object> updateQuantity(Order order) {

		if (orderDao.updateQuantity(order) > 0) {
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(new OrderResponse(202, "Quantity Updatd"));
		} else {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new OrderResponse(500, "Error in Updated Quantity"));
		}

	}

	/*********************************************************************
	 * To cancel the book order by the user it will remove book from cart.
	 * 
	 * @param String token, int bookId
	 * @return ResponseEntity<Object>
	 ********************************************************************/
	@Override
	public ResponseEntity<Object> cancelOrder(int bookId) {

		if (orderDao.deleteOrder(bookId) > 0) {
			Book book = bookDao.getBookByBookId(bookId);
			book.setQuantity(book.getQuantity() + 1);
			bookDao.updateBook(book, book.getBookName());
			return ResponseEntity.status(HttpStatus.ACCEPTED)
					.body(new OrderResponse(202, "Order Deleted SuccessFully"));
		} else {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new OrderResponse(500, "Error in Delete Order"));
		}

	}

	/***************************************************************************
	 * To confirm the order by the user after confirmation of order will Send mail
	 * to user Attached with FreeMarker template.
	 *
	 * @param String token, List<Order> order
	 * @return ResponseEntity<Object>
	 ********************************************************************/
	@Override
	public ResponseEntity<Object> confirmOrder(String token, List<Order> order) {
		if (verifyUser(token)) {
			MimeMessage message = sender.createMimeMessage();
			Map<String, Object> model = new HashMap<String, Object>();
			order.forEach(s -> {
				double temp = 0;
				temp = s.getTotal();
				finalAmount += temp;
			});
			model.put("name", userData.getFirstName());
			model.put("total", finalAmount);
			try {
				MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_RELATED,
						StandardCharsets.UTF_8.name());
				Template template = config.getTemplate("order-summery.ftl");
				String html = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
				helper.setTo(userData.getEmail());
				helper.setText(html, true);
				helper.setSubject("BookStore Order Summery");
				helper.setFrom("pati.rupesh990@gmail.com");
//				sender.send(message);
				List<Book> orderedBooks = new ArrayList<Book>();
				List<Book> fetchedBooks = bookDao.getAllBooks();

				for (Book fetchedBook : fetchedBooks) {
					for (Order fetchedOrder : order) {
						if (fetchedOrder.getBookId() == fetchedBook.getBookId()) {
							orderedBooks.add(fetchedBook);
						}
					}
				}
				String cuurentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
				Cart confirmOrder = new Cart();
				confirmOrder.setCreatedTime(cuurentTime);
				confirmOrder.setUserId(userData.getUId());
				confirmOrder.setBooksList(orderedBooks);
				order.stream().forEachOrdered(p -> {
					confirmOrder.setFinalAmount(confirmOrder.getFinalAmount() + p.getTotal());
				});

				
				orderDao.saveOrderDetails(confirmOrder);
				order.forEach(p -> {
					Cart cartOrder=orderDao.getOrder(p.getUserId(), cuurentTime);
					Quantity quantity = new Quantity();
					quantity.setBookId(p.getBookId());
					quantity.setUserId(p.getUserId());
					quantity.setQuantity(p.getQuantity());
					quantity.setCreatedTime(cuurentTime);
					quantity.setInvoiceNumber(cartOrder.getInvoiceNumber());
					quantityDao.addOrderQuantity(quantity);
				});
				orderDao.removeAllOrder(userData.getUId());
				return ResponseEntity.status(HttpStatus.ACCEPTED).body(new MailResponse("Mail Sent", "202"));
			} catch (MessagingException | IOException | TemplateException e) {
				System.out.println("Error in message sending");
				e.printStackTrace();
			}
		} else {
			throw new UserDoesNotExistException("User Does Not Exist", HttpStatus.BAD_REQUEST);
		}

		return null;
	}

	public ResponseEntity<OrderResponse> getOrderList(String token) {
		if (verifyUser(token)) {
			List<Cart> orders = orderDao.getUsersOrderList(userData.getUId());


			HashMap<Integer, List<Book>> booksList = new HashMap<>();
			orders.forEach(order -> {
				booksList.put(order.getInvoiceNumber(), order.getBooksList());
			});

			HashMap<Integer, List<Quantity>> orderQuantityList = new HashMap<Integer, List<Quantity>>();
			orders.forEach(order -> {
				booksList.forEach((key, books) -> {
					if (order.getInvoiceNumber() == key) {
						List<Quantity> qty=new ArrayList<>();
						books.forEach(book -> {	
									qty.add(quantityDao.getOrdersQuantity(userData.getUId(), book.getBookId(), order.getCreatedTime()));
						});
						orderQuantityList.put(order.getInvoiceNumber(), qty);
					}
				});
			});

			orders.forEach(order->{
				orderQuantityList.forEach((key,quantity)->{
					if(order.getInvoiceNumber()==key) {
						quantity.forEach(qty->{
							order.getBooksList().forEach(book->{
								if(qty.getBookId()==book.getBookId()) {
									book.setQuantity(qty.getQuantity());
								}
							});
						});
					}
				});
			});

			List<PlacedOrderDetail> placedOrders = new ArrayList<>();

			orders.forEach(order -> {
				
				order.getBooksList().forEach(book -> {
					PlacedOrderDetail orderDto = new PlacedOrderDetail();
					orderDto.setInvoiceNumber(order.getInvoiceNumber());
					orderDto.setCreatedTime(order.getCreatedTime());
					orderDto.setBookId(book.getBookId());
					orderDto.setBookName(book.getBookName());
					orderDto.setAuthorName(book.getAuthorName());
					orderDto.setPrice(book.getPrice());
					orderDto.setQuantity(book.getQuantity());
					orderDto.setBookImage(book.getBookImage());
					placedOrders.add(orderDto);
				});
				
			});
			
			return ResponseEntity.status(HttpStatus.ACCEPTED).body(new OrderResponse(placedOrders));
		} else {
			throw new UserDoesNotExistException("User Does Not Exist", HttpStatus.BAD_REQUEST);
		}
	}

	/*********************************************************************
	 * To verify User whether user is verified or token is valid or not. will get
	 * data from user-Api using Resttemplate.
	 * 
	 * @param String token
	 * @return boolean
	 ********************************************************************/
	public boolean verifyUser(String token) {
		log.info("-------->>>>>>>>>>>>>Calling USerApi From NotesApi<<<<<<<<<<<<<<<<--------------------");
		userData = restTemplate.getForObject("http://localhost:8092/users/" + token, UserData.class);
		log.info("--------->>>>>>>>>>>>Accessing DataFrom UserApi<<<<<<<<<<<---------------------");
		try {
			log.info("verifyUserApi Using RestTemplate From UserApi Success--------->:"
					+ (userData.getUId() == generateToken.parseToken(token)));
			log.info("erererererererererererererereererereereerererhsghgghsghgsd" + userData.getPhNo());
			return (userData.getUId() == generateToken.parseToken(token));
		} catch (SignatureVerificationException | JWTDecodeException | AlgorithmMismatchException e) {
			throw new InvalidTokenOrExpiredException("Invalid Token or Token Expired", HttpStatus.BAD_REQUEST);
		}
	}

//	@Override
//	public boolean isRatingAdded(String token, int orderId, int bookId) {
//		if(verifyUser(token)) {
//			orderDao.getOrder(bookId, userRating);
//		}
//		return false;
//	}

	
}
