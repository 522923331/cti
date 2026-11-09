package com.cti.api.web;

import com.cti.portfolio.book.BookService;
import com.cti.portfolio.book.PaperLedger;
import com.cti.portfolio.book.PaperOrderService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/portfolios")
public class BookController {

    private final BookService books;
    private final PaperOrderService orders;

    public BookController(BookService books, PaperOrderService orders) {
        this.books = books;
        this.orders = orders;
    }

    @PostMapping
    public Map<String, String> open(@RequestBody Map<String, String> body) {
        return Map.of("result", call(() -> books.open(body.get("accountId"), body.get("name"), body.get("type"), body.get("initialBalance"))));
    }

    @PostMapping("/{accountId}/deposits")
    public Map<String, String> deposit(@PathVariable String accountId, @RequestBody Map<String, String> body) {
        return Map.of("result", call(() -> books.deposit(accountId, body.get("requestId"), body.get("amount"))));
    }

    @PostMapping("/{accountId}/withdrawals")
    public Map<String, String> withdraw(@PathVariable String accountId, @RequestBody Map<String, String> body) {
        return Map.of("result", call(() -> books.withdraw(accountId, body.get("requestId"), body.get("amount"))));
    }

    @PostMapping("/{accountId}/fills")
    public Map<String, String> fill(@PathVariable String accountId, @RequestBody Map<String, String> body) {
        return Map.of("result", call(() -> books.fill(accountId, body.get("requestId"), body.get("symbol"),
                body.get("side"), body.get("quantity"), body.get("price"), body.get("fee"))));
    }

    @PostMapping("/{accountId}/marks")
    public Map<String, String> mark(@PathVariable String accountId, @RequestBody Map<String, String> body) {
        return Map.of("result", call(() -> books.mark(accountId, body.get("symbol"), body.get("price"))));
    }

    @PostMapping("/{accountId}/orders")
    public Map<String, String> order(@PathVariable String accountId, @RequestBody Map<String, String> body) {
        return callMap(() -> orders.submit(accountId, body));
    }

    @PostMapping("/{accountId}/orders/{orderId}/match")
    public Map<String, String> match(@PathVariable String accountId, @PathVariable String orderId, @RequestBody Map<String, String> body) {
        return callMap(() -> orders.match(accountId, orderId, body));
    }

    @PostMapping("/{accountId}/orders/{orderId}/cancel")
    public Map<String, String> cancel(@PathVariable String accountId, @PathVariable String orderId) {
        return callMap(() -> orders.cancel(accountId, orderId));
    }

    @PostMapping("/{accountId}/protections")
    public Map<String, String> protect(@PathVariable String accountId, @RequestBody Map<String, String> body) {
        return callMap(() -> orders.protect(accountId, body));
    }

    @PostMapping("/{accountId}/allocations")
    public JsonNode allocate(@PathVariable String accountId, @RequestBody JsonNode body) {
        try {
            return orders.allocate(accountId, body);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        }
    }

    @GetMapping("/{accountId}/orders")
    public List<Map<String, String>> orders(@PathVariable String accountId) {
        List<Map<String, String>> rows = orders.orders(accountId);
        if (rows == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这个账户");
        }
        return rows;
    }

    @PostMapping("/{accountId}/disable")
    public Map<String, String> disable(@PathVariable String accountId) {
        return Map.of("result", call(() -> books.disable(accountId)));
    }

    @GetMapping("/{accountId}")
    public BookService.AccountView account(@PathVariable String accountId) {
        BookService.AccountView view = books.account(accountId);
        if (view == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这个账户");
        }
        return view;
    }

    @GetMapping("/{accountId}/ledger")
    public List<PaperLedger.LedgerEntry> ledger(@PathVariable String accountId) {
        List<PaperLedger.LedgerEntry> rows = books.ledger(accountId);
        if (rows == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这个账户");
        }
        return rows;
    }

    @GetMapping("/{accountId}/positions")
    public List<PaperLedger.Position> positions(@PathVariable String accountId) {
        List<PaperLedger.Position> rows = books.positions(accountId);
        if (rows == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这个账户");
        }
        return rows;
    }

    private Map<String, String> callMap(MapAction action) {
        try {
            return action.run();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        }
    }

    private String call(Action action) {
        try {
            return action.run();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        }
    }

    private interface Action {
        String run();
    }

    private interface MapAction {
        Map<String, String> run();
    }
}
