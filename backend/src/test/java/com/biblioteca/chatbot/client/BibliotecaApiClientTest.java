package com.biblioteca.chatbot.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.biblioteca.chatbot.config.BibliotecaProperties;
import com.biblioteca.chatbot.model.Book;
import com.biblioteca.chatbot.model.LoanPage;
import com.biblioteca.chatbot.model.Member;
import com.biblioteca.chatbot.model.Stats;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de la biblioteca contra un servidor simulado (MockRestServiceServer): URL de cada
 * consulta, conversión del JSON real de la API a las clases del modelo y errores.
 */
class BibliotecaApiClientTest {

    private static final String BASE = "http://biblioteca.test/api";

    private MockRestServiceServer server;
    private BibliotecaApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BibliotecaApiClient(builder.build(), JsonMapper.builder().build(),
                new BibliotecaProperties(BASE, Duration.ofSeconds(1), 14, 3, 5));
    }

    /** El texto se codifica en la URL y los campos que el modelo no usa se ignoran. */
    @Test
    void booksEncodesQueryAndIgnoresUnknownFields() {
        server.expect(requestTo(BASE + "/books?q=garc%C3%ADa%20%26%20cia")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"id":1,"title":"Cien años de soledad","author":"Gabriel García Márquez","genre":"Novela",
                          "totalCopies":3,"availableCopies":1,"available":true,"campoNuevo":"x"}]
                        """, MediaType.APPLICATION_JSON));

        List<Book> books = client.books("garcía & cia");

        assertThat(books).containsExactly(new Book(1L, "Cien años de soledad", "Gabriel García Márquez", "Novela", 3, 1));
        server.verify();
    }

    /** Fechas ISO a LocalDate y paginación. */
    @Test
    void loansParsesPage() {
        server.expect(requestTo(BASE + "/loans?status=OVERDUE&q=ana&page=0&size=100")).andRespond(withSuccess("""
                {"content":[{"id":21,"bookTitle":"Rayuela","memberName":"Ana Torres","loanDate":"2026-09-17",
                  "dueDate":"2026-10-01","returnDate":null,"status":"OVERDUE","renewals":0}],
                 "page":0,"size":100,"totalElements":1,"totalPages":1}
                """, MediaType.APPLICATION_JSON));

        LoanPage page = client.loans("OVERDUE", "ana", 100);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().getFirst().dueDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(page.content().getFirst().overdue()).isTrue();
    }

    @Test
    void membersAndStats() {
        server.expect(requestTo(BASE + "/members")).andRespond(withSuccess(
                "[{\"id\":1,\"name\":\"Ana\",\"email\":\"ana@example.com\",\"activeLoans\":2,\"totalLoans\":7}]",
                MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/stats")).andRespond(withSuccess("""
                {"totalBooks":10,"totalMembers":6,"activeLoans":6,"overdueLoans":2,"returnedLoans":15,
                 "loansByMonth":[],"topBooks":[{"label":"Dune","count":5}],"loansByGenre":[],"topMembers":[]}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.members()).containsExactly(new Member(1L, "Ana", "ana@example.com", 2, 7));
        Stats stats = client.stats();
        assertThat(stats.overdueLoans()).isEqualTo(2);
        assertThat(stats.topBooks()).containsExactly(new Stats.Entry("Dune", 5));
    }

    /** Un error de la biblioteca se convierte en un mensaje con su código y su texto. */
    @Test
    void apiErrorKeepsMessage() {
        server.expect(requestTo(BASE + "/stats")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":500,\"message\":\"Error interno del servidor\"}"));

        assertThatThrownBy(() -> client.stats())
                .isInstanceOf(BibliotecaApiException.class)
                .hasMessageContaining("HTTP 500")
                .hasMessageContaining("Error interno del servidor");
    }

    @Test
    void availabilityCheck() {
        server.expect(requestTo(BASE + "/loans/summary")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/loans/summary")).andRespond(withResourceNotFound());

        assertThat(client.isAvailable()).isTrue();
        assertThat(client.isAvailable()).isFalse();
    }
}
