# Socket Communication Protocol - Week 2 Baseline

## 1. Overview
Giao thức trao đổi dữ liệu dạng Plain Text ngăn cách bởi ký tự `|` (Pipe delimiter), kết thúc bằng ký tự xuống dòng `\n`. Mã hóa: UTF-8.

## 2. Request & Response Specification

### 2.1 LOGIN
* **Request:** `LOGIN|<account>|<password>`
* **Response:**
    * Thành công: `SUCCESS|LOGIN|<token>|<account>|<role>`
    * Thất bại: `ERROR|<message>`

### 2.2 CREATE_ITEM
* **Request:** `CREATE_ITEM|<token>|<name>|<itemType>|<description>`
* **Yêu cầu:** Session role phải là `SELLER`. `itemType` thuộc: `CARD`, `FIGURE`, `POSTER`.
* **Response:**
    * Thành công: `SUCCESS|CREATE_ITEM|<itemId>`
    * Thất bại: `ERROR|<message>`

### 2.3 CREATE_AUCTION
* **Request:** `CREATE_AUCTION|<token>|<itemId>|<startPriceCents>|<minIncrementCents>|<durationHours>`
* **Yêu cầu:** Session role phải là `SELLER`. `durationHours` >= 1.
* **Response:**
    * Thành công: `SUCCESS|CREATE_AUCTION|<auctionId>`
    * Thất bại: `ERROR|<message>`

### 2.4 LIST_AUCTIONS
* **Request:** `LIST_AUCTIONS`
* **Response:** `SUCCESS|LIST_AUCTIONS|COUNT=<number>`

### 2.5 QUIT / EXIT
* **Request:** `QUIT` hoặc `EXIT`
* **Response:** `BYE` và server đóng socket.