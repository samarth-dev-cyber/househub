package com.househub;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ExpenseController {

    public record Balance(String name, BigDecimal paid, BigDecimal owed, BigDecimal net) {}

    private static final Set<String> ALLOWED = Set.of("png", "jpg", "jpeg", "webp", "gif", "pdf");

    private final MemberRepository members;
    private final ExpenseRepository expenses;
    private final Path uploadDir;

    public ExpenseController(MemberRepository members, ExpenseRepository expenses,
                             @Value("${househub.upload-dir}") String dir) throws IOException {
        this.members = members;
        this.expenses = expenses;
        this.uploadDir = Paths.get(dir).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
    }

    @GetMapping("/")
    public String home(Model model) {
        List<Member> memberList = members.findAll(Sort.by("name"));
        List<Expense> expenseList = expenses.findAll(Sort.by(Sort.Direction.DESC, "date", "id"));

        Map<Long, BigDecimal> paid = new HashMap<>();
        Map<Long, BigDecimal> owed = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Expense e : expenseList) {
            total = total.add(e.amount);
            paid.merge(e.paidBy.id, e.amount, BigDecimal::add);
            BigDecimal share = e.getShare();
            for (Member p : e.participants) owed.merge(p.id, share, BigDecimal::add);
        }
        List<Balance> balances = new ArrayList<>();
        for (Member m : memberList) {
            BigDecimal p = paid.getOrDefault(m.id, BigDecimal.ZERO);
            BigDecimal o = owed.getOrDefault(m.id, BigDecimal.ZERO);
            balances.add(new Balance(m.name, p, o, p.subtract(o)));
        }
        model.addAttribute("members", memberList);
        model.addAttribute("expenses", expenseList);
        model.addAttribute("balances", balances);
        model.addAttribute("total", total);
        model.addAttribute("today", LocalDate.now());
        return "index";
    }

    @PostMapping("/members")
    public String addMember(@RequestParam String name, RedirectAttributes ra) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.length() > 60) {
            ra.addFlashAttribute("error", "Enter a member name of 1 to 60 characters.");
        } else if (members.existsByNameIgnoreCase(clean)) {
            ra.addFlashAttribute("error", clean + " is already a member.");
        } else {
            members.save(new Member(clean));
        }
        return "redirect:/";
    }

    @PostMapping("/expenses")
    public String addExpense(@RequestParam String title,
                             @RequestParam BigDecimal amount,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @RequestParam Long paidById,
                             @RequestParam(required = false) List<Long> participantIds,
                             @RequestParam MultipartFile receipt,
                             RedirectAttributes ra) throws IOException {
        if (title.isBlank() || amount.signum() <= 0) {
            return fail(ra, "Enter a description and an amount greater than zero.");
        }
        if (participantIds == null || participantIds.isEmpty()) {
            return fail(ra, "Select at least one member to share this expense.");
        }
        Optional<Member> payer = members.findById(paidById);
        if (payer.isEmpty()) return fail(ra, "Choose who paid.");
        if (receipt.isEmpty()) {
            return fail(ra, "Attach the bill, screenshot or payment confirmation.");
        }
        String original = Optional.ofNullable(receipt.getOriginalFilename()).orElse("receipt");
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED.contains(ext)) {
            return fail(ra, "Upload a PNG, JPG, WEBP, GIF or PDF file.");
        }

        Expense e = new Expense();
        e.title = title.trim();
        e.amount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        e.date = date;
        e.paidBy = payer.get();
        e.participants.addAll(members.findAllById(participantIds));
        if (e.participants.isEmpty()) return fail(ra, "Select at least one member to share this expense.");

        String stored = UUID.randomUUID() + "." + ext;
        Files.copy(receipt.getInputStream(), uploadDir.resolve(stored));
        e.receiptFile = stored;
        e.receiptName = original.length() > 120 ? original.substring(0, 120) : original;
        expenses.save(e);
        return "redirect:/";
    }

    @PostMapping("/expenses/{id}/delete")
    public String delete(@PathVariable Long id) throws IOException {
        Optional<Expense> e = expenses.findById(id);
        if (e.isPresent()) {
            if (e.get().receiptFile != null) Files.deleteIfExists(uploadDir.resolve(e.get().receiptFile));
            expenses.delete(e.get());
        }
        return "redirect:/";
    }

    @GetMapping("/receipts/{name:.+}")
    public ResponseEntity<Resource> receipt(@PathVariable String name) throws IOException {
        Path file = uploadDir.resolve(name).normalize();
        if (!file.startsWith(uploadDir) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        String type = Optional.ofNullable(Files.probeContentType(file)).orElse("application/octet-stream");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new FileSystemResource(file));
    }

    @GetMapping("/privacy")
    public String privacy() { return "privacy"; }

    @GetMapping("/terms")
    public String terms() { return "terms"; }

    private String fail(RedirectAttributes ra, String message) {
        ra.addFlashAttribute("error", message);
        return "redirect:/";
    }
}
