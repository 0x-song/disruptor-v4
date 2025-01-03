package com.sz.disruptor.model;

/**
 * @Author
 * @Date 2024-12-08 15:29
 * @Version 1.0
 */
public class OrderEventModel {

    private String message;
    private int price;

    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public int getPrice() {
        return price;
    }
    public void setPrice(int price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return "OrderEventModel{" +
                "message='" + message + '\'' +
                ", price=" + price +
                '}';
    }
}
