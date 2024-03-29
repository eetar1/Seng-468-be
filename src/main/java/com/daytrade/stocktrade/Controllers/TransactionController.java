package com.daytrade.stocktrade.Controllers;

import com.daytrade.stocktrade.Models.Account;
import com.daytrade.stocktrade.Models.Command;
import com.daytrade.stocktrade.Models.Enums;
import com.daytrade.stocktrade.Models.Exceptions.BadRequestException;
import com.daytrade.stocktrade.Models.Exceptions.EntityMissingException;
import com.daytrade.stocktrade.Models.Transactions.PendingTransaction;
import com.daytrade.stocktrade.Models.Transactions.Transaction;
import com.daytrade.stocktrade.Services.LoggerService;
import com.daytrade.stocktrade.Services.TransactionService;
import java.util.HashMap;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
public class TransactionController {

  private final TransactionService transactionService;
  private final LoggerService loggerService;

  @Autowired
  public TransactionController(TransactionService transactionService, LoggerService loggerService) {
    this.transactionService = transactionService;
    this.loggerService = loggerService;
  }

  @GetMapping("/quote/{stockSym}")
  public Map<String, Double> getQuote(
      @PathVariable("stockSym") String stockSym,
      @RequestParam(name = "transactionId") String transId)
      throws InterruptedException {
    String name = SecurityContextHolder.getContext().getAuthentication().getName();
    Map<String, Double> out = new HashMap<>();
    Double quote = transactionService.getQuote(name, stockSym, transId).getUnitPrice();
    out.put(stockSym, quote);
    loggerService.createCommandLog(name, transId, Enums.CommandType.QUOTE, stockSym, null, null);
    return out;
  }

  @PostMapping("/order/simple")
  public Transaction createSimpleOrder(@Valid @RequestBody PendingTransaction transaction)
      throws Exception {
    if (transaction.getType().equals(Enums.TransactionType.SELL)
        || transaction.getType().equals(Enums.TransactionType.BUY)) {
      Enums.CommandType cmdType =
          transaction.getType().equals(Enums.TransactionType.SELL)
              ? Enums.CommandType.SELL
              : Enums.CommandType.BUY;
      transaction.setUserName(SecurityContextHolder.getContext().getAuthentication().getName());
      Transaction newTransaction =
          transaction.getType().equals(Enums.TransactionType.BUY)
              ? transactionService.createSimpleBuyTransaction(transaction)
              : transactionService.createSimpleSellTransaction(transaction);
      loggerService.createTransactionCommandLog(transaction, cmdType, null);
      return newTransaction;
    } else {
      loggerService.createTransactionErrorLog(
          transaction, Enums.CommandType.BUY, "Incorrect transaction type");
      throw new BadRequestException("Not correct transaction type");
    }
  }

  @PostMapping("/order/limit")
  public Transaction createLimitOrder(@Valid @RequestBody PendingTransaction transaction) {
    if (transaction.getType().equals(Enums.TransactionType.SELL_AT)
        || transaction.getType().equals(Enums.TransactionType.BUY_AT)) {
      Enums.CommandType cmdType =
          transaction.getType().equals(Enums.TransactionType.SELL_AT)
              ? Enums.CommandType.SET_SELL_AMOUNT
              : Enums.CommandType.SET_BUY_AMOUNT;
      transaction.setUserName(SecurityContextHolder.getContext().getAuthentication().getName());
      Transaction newTransaction = transactionService.createLimitTransaction(transaction);
      loggerService.createTransactionCommandLog(transaction, cmdType, null);
      return newTransaction;
    } else {
      loggerService.createTransactionErrorLog(
          transaction, Enums.CommandType.SET_BUY_AMOUNT, "Incorrect transaction type");
      throw new BadRequestException("Not correct transaction type");
    }
  }

  @PostMapping("/setBuy/trigger")
  public Transaction triggerBuyLimitOrder(@Valid @RequestBody Transaction newTransaction) {
    if (newTransaction.getType().equals(Enums.TransactionType.BUY_AT)) {
      String name = SecurityContextHolder.getContext().getAuthentication().getName();
      newTransaction.setUserName(name);
      Command cmd = new Command();
      cmd.setTransactionId(newTransaction.getTransactionId());
      cmd.setUsername(name);
      cmd.setType(Enums.CommandType.SET_BUY_TRIGGER);
      PendingTransaction savedTransaction = transactionService.getPendingLimitBuyTransactions(cmd);
      Transaction updatedTransaction =
          transactionService.triggerLimitTransaction(savedTransaction, newTransaction);
      loggerService.createTransactionCommandLog(
          newTransaction, Enums.CommandType.SET_BUY_TRIGGER, null);
      return updatedTransaction;
    } else {
      loggerService.createTransactionErrorLog(
          newTransaction, Enums.CommandType.SET_BUY_TRIGGER, "Incorrect transaction type");
      throw new BadRequestException("Not correct transaction type");
    }
  }

  @PostMapping("/setSell/trigger")
  public Transaction triggerSellLimitOrder(@Valid @RequestBody Transaction newTransaction) {
    if (newTransaction.getType().equals(Enums.TransactionType.SELL_AT)) {
      String name = SecurityContextHolder.getContext().getAuthentication().getName();
      newTransaction.setUserName(name);

      Command cmd = new Command();
      cmd.setTransactionId(newTransaction.getTransactionId());
      cmd.setUsername(name);
      cmd.setType(Enums.CommandType.SET_SELL_TRIGGER);
      PendingTransaction savedTransaction = transactionService.getPendingLimitSellTransactions(cmd);
      Transaction updatedTransaction =
          transactionService.triggerLimitTransaction(savedTransaction, newTransaction);
      loggerService.createTransactionCommandLog(
          newTransaction, Enums.CommandType.SET_SELL_TRIGGER, null);
      return updatedTransaction;
    } else {
      loggerService.createTransactionErrorLog(
          newTransaction, Enums.CommandType.SET_SELL_TRIGGER, "Incorrect transaction type");
      throw new BadRequestException("Not correct transaction type");
    }
  }

  @PostMapping("/setSell/cancel/{stock}")
  public Transaction cancelSellLimitOrder(
      @Valid @RequestBody Command cmd, @PathVariable("stock") String stockTicker) {
    String name = SecurityContextHolder.getContext().getAuthentication().getName();
    cmd.setUsername(name);
    cmd.setType(Enums.CommandType.CANCEL_SET_SELL);
    try {
      Transaction savedTransaction =
          transactionService.getPendingLimitSellTransactionsByTicker(stockTicker);
      savedTransaction.setStatus(Enums.TransactionStatus.CANCELED);
      savedTransaction.setTransactionId(cmd.getTransactionId());
      Transaction cancelledTransaction =
          transactionService.cancelSellLimitTransaction(savedTransaction);
      loggerService.createCommandLog(
          name, cmd.getTransactionId(), cmd.getType(), stockTicker, null, null);
      return cancelledTransaction;
    } catch (EntityMissingException ex) {
      Transaction savedTransaction =
          transactionService.getCommittedLimitSellTransactionsByTicker(stockTicker, cmd);
      savedTransaction.setStatus(Enums.TransactionStatus.CANCELED);
      loggerService.createErrorEventLog(
          name,
          cmd.getTransactionId(),
          Enums.CommandType.CANCEL_SET_SELL,
          stockTicker,
          null,
          null,
          "Invalid Request");
      return transactionService.cancelSellLimitTransaction(savedTransaction);
    }
  }

  @PostMapping("/setBuy/cancel/{stock}")
  public Transaction cancelBuyLimitOrder(
      @Valid @RequestBody Command cmd, @PathVariable("stock") String stockTicker) {
    String name = SecurityContextHolder.getContext().getAuthentication().getName();
    cmd.setUsername(name);
    cmd.setType(Enums.CommandType.CANCEL_SET_BUY);
    try {
      Transaction savedTransaction =
          transactionService.getPendingLimitBuyTransactionsByTicker(stockTicker);
      savedTransaction.setStatus(Enums.TransactionStatus.CANCELED);
      savedTransaction.setTransactionId(cmd.getTransactionId());
      Transaction cancelledTransaction =
          transactionService.cancelBuyLimitTransaction(savedTransaction);
      loggerService.createCommandLog(
          name, cmd.getTransactionId(), cmd.getType(), stockTicker, null, null);
      return cancelledTransaction;
    } catch (EntityMissingException ex) {
      Transaction savedTransaction =
          transactionService.getCommittedLimitBuyTransactionsByTicker(stockTicker, cmd);
      return transactionService.cancelBuyLimitTransaction(savedTransaction);
    }
  }

  @PostMapping("/buy/cancel")
  public Transaction cancelBuyOrder(@Valid @RequestBody Command cmd) {
    String name = SecurityContextHolder.getContext().getAuthentication().getName();
    cmd.setUsername(name);
    cmd.setType(Enums.CommandType.CANCEL_BUY);
    Transaction transaction = transactionService.getPendingBuyTransactions(cmd);
    transaction.setStatus(Enums.TransactionStatus.CANCELED);
    Transaction cancelledTransaction = transactionService.cancelTransaction(transaction);
    loggerService.createCommandLog(name, cmd.getTransactionId(), cmd.getType(), null, null, null);
    return cancelledTransaction;
  }

  @PostMapping("/sell/cancel")
  public Transaction cancelSellOrder(@Valid @RequestBody Command cmd) {
    String name = SecurityContextHolder.getContext().getAuthentication().getName();
    cmd.setUsername(name);
    cmd.setType(Enums.CommandType.CANCEL_SELL);
    Transaction transaction = transactionService.getPendingSellTransactions(cmd);
    transaction.setStatus(Enums.TransactionStatus.CANCELED);
    Transaction cancelledTransaction = transactionService.cancelTransaction(transaction);
    loggerService.createCommandLog(name, cmd.getTransactionId(), cmd.getType(), null, null, null);
    return cancelledTransaction;
  }

  @PostMapping("/sell/commit")
  public Account commitSimpleSellOrder(@Valid @RequestBody Command cmd) {
    String name = SecurityContextHolder.getContext().getAuthentication().getName();
    cmd.setUsername(name);
    cmd.setType(Enums.CommandType.COMMIT_SELL);
    PendingTransaction transaction = transactionService.getPendingSellTransactions(cmd);
    Transaction commitedTransaction = transactionService.commitSimpleOrder(transaction);
    loggerService.createCommandLog(name, cmd.getTransactionId(), cmd.getType(), null, null, null);
    return transactionService.updateAccount(commitedTransaction);
  }

  @PostMapping("/buy/commit")
  public Account commitSimpleBuyOrder(@Valid @RequestBody Command cmd) {
    String name = SecurityContextHolder.getContext().getAuthentication().getName();
    cmd.setUsername(name);
    cmd.setType(Enums.CommandType.COMMIT_BUY);
    PendingTransaction transaction = transactionService.getPendingBuyTransactions(cmd);
    Transaction committedTransaction = transactionService.commitSimpleOrder(transaction);
    loggerService.createCommandLog(name, cmd.getTransactionId(), cmd.getType(), null, null, null);
    return transactionService.updateAccount(committedTransaction);
  }
}
