package com.daytrade.stocktrade.Models;

import java.time.Instant;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.context.SecurityContextHolder;

@Document(collection = "transactions")
@Data
public class Transaction {
  @Id public String id;

  private Enums.TransactionStatus status;

  @NotNull private Enums.TransactionType type;

  @NotNull @NotBlank private String stockCode;

  private Long stockAmount;

  private Double cashAmount;

  private String userName = SecurityContextHolder.getContext().getAuthentication().getName();

  @CreatedDate private Instant createdDate;

  private Double unitPrice;
}