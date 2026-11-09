package com.cti.api.web;

import com.cti.portfolio.book.BookService;
import com.cti.portfolio.book.PaperOrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class BookPageController {

    private final BookService books;
    private final PaperOrderService orders;

    public BookPageController(BookService books, PaperOrderService orders) {
        this.books = books;
        this.orders = orders;
    }

    @GetMapping("/accounts")
    public String list(Model model) {
        model.addAttribute("accounts", books.accounts());
        return "accounts";
    }

    @GetMapping("/accounts/{accountId}")
    public String one(@PathVariable String accountId, Model model) {
        BookService.AccountView account = books.account(accountId);
        if (account == null) {
            return "missing";
        }
        model.addAttribute("account", account);
        model.addAttribute("positions", books.positions(accountId));
        model.addAttribute("ledger", books.ledger(accountId));
        model.addAttribute("orders", orders.orders(accountId));
        return "account";
    }
}
