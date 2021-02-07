package com.daytrade.stocktrade.Services;

import com.daytrade.stocktrade.Models.Enums;
import com.daytrade.stocktrade.Models.Quote;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {

  private final LoggerService loggerService;

  public QuoteService(LoggerService loggerService) {
    this.loggerService = loggerService;
  }

  private Socket qsSocket = null;
  private PrintWriter out = null;
  private BufferedReader in = null;

  public Quote quote(String userid, String stockSymbol, String transactionNumber) throws Exception {
    try {
      qsSocket = new Socket("quoteserver.seng.uvic.ca", 4442);
      out = new PrintWriter(qsSocket.getOutputStream(), true);
      in = new BufferedReader(new InputStreamReader(qsSocket.getInputStream()));
    } catch (UnknownHostException e) {
      loggerService.createErrorEventLog(
          userid,
          transactionNumber,
          Enums.CommandType.QUOTE,
          stockSymbol,
          null,
          null,
          "UnknownHostException");
    } catch (IOException e) {
      loggerService.createErrorEventLog(
          userid,
          transactionNumber,
          Enums.CommandType.QUOTE,
          stockSymbol,
          null,
          null,
          "IOException");
    } catch (Exception e) {
      loggerService.createErrorEventLog(
          userid, transactionNumber, Enums.CommandType.QUOTE, stockSymbol, null, null, "Exception");
    }

    String fromServer;

    fromServer = in.readLine();

    System.out.print(fromServer);
    // TODO: remove message in final revision

    out.close();
    in.close();
    qsSocket.close();

    // serverReponse is returned as "quote, symbol, userid, timestamp, cryptokey"
    String[] serverResponse = fromServer.split(",");

    Double quoteValue = parseQuoteToDouble(serverResponse[0]);

    Long serverTime = parseTimetoLong(serverResponse[3]);

    Instant timestamp = Instant.ofEpochMilli(serverTime);

    String cryptokey = serverResponse[4];

    loggerService.createQuoteServerLog(
        userid, transactionNumber, stockSymbol, quoteValue, timestamp, cryptokey);

    Quote quote =
        new Quote(userid, transactionNumber, stockSymbol, quoteValue, timestamp, cryptokey);

    return quote;
  }

  private Double parseQuoteToDouble(String quote) {
    Double unitPrice = Double.parseDouble(quote);
    return unitPrice;
  }

  private Long parseTimetoLong(String time) {
    Long timestamp = Long.parseLong(time);
    return timestamp;
  }
}