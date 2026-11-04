package com.khonhaminh.inventory;
import com.khonhaminh.product.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Table(name="inventory_transactions")
public class InventoryTransaction {
 public enum Type{IN,OUT,OPENING,ADJUST_IN,ADJUST_OUT} public enum Status{COMPLETED,VOIDED}
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true,length=40) private String code;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Type type;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id") private Product product;
 @Column(name="product_name_snapshot",nullable=false,length=180) private String productNameSnapshot;
 @Column(name="unit_snapshot",nullable=false,length=60) private String unitSnapshot;
 @Column(nullable=false,precision=19,scale=3) private BigDecimal quantity;
 @Column(name="entered_quantity",precision=19,scale=3) private BigDecimal enteredQuantity;
 @Column(name="entered_unit",length=60) private String enteredUnit;
 @Column(name="conversion_factor",precision=19,scale=3) private BigDecimal conversionFactor;
 @Column(name="source_key",length=220) private String sourceKey;
 @Column(name="unit_cost",precision=19,scale=2) private BigDecimal unitCost;
 @Column(length=500) private String note;
 @Column(length=180) private String supplier;
 @Column(name="receiver_name",length=180) private String receiverName;
 @Column(name="invoice_sent",nullable=false) private boolean invoiceSent=false;
 @Column(length=180) private String department;
 @Column(name="employee_name",length=180) private String employeeName;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @Column(name="voided_at") private LocalDateTime voidedAt;
 @PrePersist void pre(){if(createdAt==null)createdAt=LocalDateTime.now();}
 public Long getId(){return id;} public String getCode(){return code;} public void setCode(String v){code=v;} public Type getType(){return type;} public void setType(Type v){type=v;} public Status getStatus(){return status;} public void setStatus(Status v){status=v;} public Product getProduct(){return product;} public void setProduct(Product v){product=v;} public String getProductNameSnapshot(){return productNameSnapshot;} public void setProductNameSnapshot(String v){productNameSnapshot=v;} public String getUnitSnapshot(){return unitSnapshot;} public void setUnitSnapshot(String v){unitSnapshot=v;} public BigDecimal getQuantity(){return quantity;} public void setQuantity(BigDecimal v){quantity=v;} public BigDecimal getEnteredQuantity(){return enteredQuantity;} public void setEnteredQuantity(BigDecimal v){enteredQuantity=v;} public String getEnteredUnit(){return enteredUnit;} public void setEnteredUnit(String v){enteredUnit=v;} public BigDecimal getConversionFactor(){return conversionFactor;} public void setConversionFactor(BigDecimal v){conversionFactor=v;} public String getSourceKey(){return sourceKey;} public void setSourceKey(String v){sourceKey=v;} public BigDecimal getUnitCost(){return unitCost;} public void setUnitCost(BigDecimal v){unitCost=v;} public String getNote(){return note;} public void setNote(String v){note=v;} public String getSupplier(){return supplier;} public void setSupplier(String v){supplier=v;} public String getReceiverName(){return receiverName;} public void setReceiverName(String v){receiverName=v;} public boolean isInvoiceSent(){return invoiceSent;} public void setInvoiceSent(boolean v){invoiceSent=v;} public String getDepartment(){return department;} public void setDepartment(String v){department=v;} public String getEmployeeName(){return employeeName;} public void setEmployeeName(String v){employeeName=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getVoidedAt(){return voidedAt;} public void setVoidedAt(LocalDateTime v){voidedAt=v;}
}
