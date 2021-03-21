package com.daytrade.stocktrade.Services;

import com.daytrade.stocktrade.Models.Enums;
import com.daytrade.stocktrade.Models.Exceptions.BadRequestException;
import com.daytrade.stocktrade.Models.Quote;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.Instant;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {

  private final LoggerService loggerService;
  private final CacheService cacheService;
  // This locks through the redis to support multiple servers
  private final RLock mutex;
  private static double delay = 50;

  @Autowired
  public QuoteService(
      LoggerService loggerService, CacheService cacheService, RedissonClient redissonClient) {
    this.loggerService = loggerService;
    this.cacheService = cacheService;
    this.mutex = redissonClient.getLock("quote-service-lock");
  }

  public Quote getQuote(String userId, String stockSymbol, String transactionNumber)
      throws InterruptedException {
    Quote cachedQuote = cacheService.getCacheQuote(stockSymbol);
    if (cachedQuote == null) {
      Socket qsSocket = null;
      PrintWriter out = null;
      BufferedReader in = null;
      mutex.lock();
      try {
        qsSocket = new Socket("192.168.4.2", 4442);
        out = new PrintWriter(qsSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(qsSocket.getInputStream()));
      } catch (UnknownHostException e) {
        loggerService.createErrorEventLog(
            userId,
            transactionNumber,
            Enums.CommandType.QUOTE,
            stockSymbol,
            null,
            null,
            "UnknownHostException");
      } catch (IOException e) {
        loggerService.createErrorEventLog(
            userId,
            transactionNumber,
            Enums.CommandType.QUOTE,
            stockSymbol,
            null,
            null,
            "IOException");
      } catch (Exception e) {
        loggerService.createErrorEventLog(
            userId,
            transactionNumber,
            Enums.CommandType.QUOTE,
            stockSymbol,
            null,
            null,
            "Exception");
      }

      try {
        // System.out.println("Connected");
        if (out != null) {
          // I don't think we need these replaces but just incase
          out.println(
              stockSymbol.replace("\n", "").replace("\r", "")
                  + ","
                  + userId.replace("\n", "").replace("\r", ""));
        }
        // Larger delay at startup, gradually decrease to 8ms
        if (delay > 8) {
          delay = delay * 0.99;
        } else {
          delay = 8;
        }
        Thread.sleep((long) delay);
        mutex.unlock();
        String fromServer = "";
        if (in != null) {
          fromServer = in.readLine();
        }

        // System.out.print(fromServer);
        // TODO: remove message in final revision
        if (out != null) {
          out.close();
        }
        if (in != null) {
          in.close();
        }
        if (qsSocket != null) {
          qsSocket.close();
        }

        // serverReponse is returned as "quote, symbol, userid, timestamp, cryptokey"
        String[] serverResponse = fromServer.split(",");

        Double quoteValue = parseQuoteToDouble(serverResponse[0]);

        Long serverTime = parseTimetoLong(serverResponse[3]);

        Instant timestamp = Instant.ofEpochMilli(serverTime);

        String cryptokey = serverResponse[4];

        loggerService.createQuoteServerLog(
            userId, transactionNumber, stockSymbol, quoteValue, timestamp, cryptokey);
        Quote freshQuote =
            new Quote(userId, transactionNumber, stockSymbol, quoteValue, timestamp, cryptokey);
        cacheService.populateCacheQuote(freshQuote, stockSymbol);
        return freshQuote;
      } catch (IOException ex) {
        loggerService.createErrorEventLog(
            userId,
            transactionNumber,
            Enums.CommandType.QUOTE,
            stockSymbol,
            null,
            null,
            "IO exception: " + ex.getMessage());
        throw new BadRequestException("Bad");
      } catch (Exception e) {
        loggerService.createErrorEventLog(
            userId,
            transactionNumber,
            Enums.CommandType.QUOTE,
            stockSymbol,
            null,
            null,
            "Error: " + e.getMessage());
        throw new BadRequestException("Big Bad");
      }
    }
    loggerService.createSystemEventLog(
        userId,
        transactionNumber,
        Enums.CommandType.QUOTE,
        stockSymbol,
        null,
        cachedQuote.getUnitPrice());
    return cachedQuote;
  }

  private Double parseQuoteToDouble(String quote) {
    return Double.parseDouble(quote);
  }

  private Long parseTimetoLong(String time) {
    return Long.parseLong(time);
  }
}
