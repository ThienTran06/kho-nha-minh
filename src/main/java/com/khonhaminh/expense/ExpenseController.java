package com.khonhaminh.expense;
import com.khonhaminh.common.ApiException;import com.khonhaminh.audit.AuditLogService;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.time.LocalDate;import java.util.List;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/expenses")
public class ExpenseController {
 private final ExpenseRepository repo;private final AuditLogService audit;public ExpenseController(ExpenseRepository r,AuditLogService a){repo=r;audit=a;}
 public record Req(@NotNull LocalDate expenseDate,@NotBlank @Size(max=100) String category,@NotNull @DecimalMin(value="0.01") BigDecimal amount,@Size(max=500) String note){}
 @GetMapping public List<Expense> list(){return repo.findAllByOrderByExpenseDateDescIdDesc();}
 @PostMapping public Expense create(@Valid @RequestBody Req r){Expense e=new Expense();apply(e,r);e=repo.save(e);audit.log("EXPENSE_CREATE","EXPENSE",e.getId(),"Ghi chi phí "+e.getCategory()+": "+e.getAmount());return e;}
 @PutMapping("/{id}") public Expense update(@PathVariable Long id,@Valid @RequestBody Req r){Expense e=repo.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy chi phí"));apply(e,r);e=repo.save(e);audit.log("EXPENSE_UPDATE","EXPENSE",e.getId(),"Sửa chi phí "+e.getCategory()+": "+e.getAmount());return e;}
 @DeleteMapping("/{id}") public void delete(@PathVariable Long id){Expense e=repo.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy chi phí"));repo.deleteById(id);audit.log("EXPENSE_DELETE","EXPENSE",id,"Xóa chi phí "+e.getCategory()+": "+e.getAmount());}
 private static void apply(Expense e,Req r){e.setExpenseDate(r.expenseDate());e.setCategory(r.category().trim());e.setAmount(r.amount());e.setNote(r.note()==null||r.note().isBlank()?null:r.note().trim());}
}
