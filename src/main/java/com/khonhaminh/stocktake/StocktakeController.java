package com.khonhaminh.stocktake;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/stocktakes")
public class StocktakeController {
 private final StocktakeService service; private final StocktakeRepository repo;
 public StocktakeController(StocktakeService s,StocktakeRepository r){service=s;repo=r;}
 public record Req(@NotNull Long productId,@NotNull @DecimalMin("0.0") BigDecimal actualQuantity,@Size(max=500) String note,LocalDateTime countedAt){}
 public record View(Long id,Long productId,String productName,String unit,BigDecimal systemQuantity,BigDecimal actualQuantity,BigDecimal differenceQuantity,String note,LocalDateTime countedAt){static View of(Stocktake s){return new View(s.getId(),s.getProduct().getId(),s.getProductNameSnapshot(),s.getUnitSnapshot(),s.getSystemQuantity(),s.getActualQuantity(),s.getDifferenceQuantity(),s.getNote(),s.getCountedAt());}}
 @GetMapping public List<View> list(){return repo.findTop100ByOrderByCountedAtDesc().stream().map(View::of).toList();}
 @PostMapping public View create(@Valid @RequestBody Req r){return View.of(service.count(r.productId(),r.actualQuantity(),r.note(),r.countedAt()));}
}
