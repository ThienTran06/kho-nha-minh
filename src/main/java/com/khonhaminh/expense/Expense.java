package com.khonhaminh.expense;
import jakarta.persistence.*;import java.math.BigDecimal;import java.time.*;
@Entity @Table(name="expenses")
public class Expense {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="expense_date",nullable=false) private LocalDate expenseDate;
 @Column(nullable=false,length=100) private String category;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
 @Column(length=500) private String note;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
 @PrePersist void pre(){var n=LocalDateTime.now();createdAt=n;updatedAt=n;} @PreUpdate void upd(){updatedAt=LocalDateTime.now();}
 public Long getId(){return id;} public LocalDate getExpenseDate(){return expenseDate;} public void setExpenseDate(LocalDate v){expenseDate=v;} public String getCategory(){return category;} public void setCategory(String v){category=v;} public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;} public String getNote(){return note;} public void setNote(String v){note=v;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
