package org.example.Amazon;

import org.example.Amazon.Cost.DeliveryPrice;
import org.example.Amazon.Cost.ExtraCostForElectronics;
import org.example.Amazon.Cost.ItemType;
import org.example.Amazon.Cost.PriceRule;
import org.example.Amazon.Cost.RegularCost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Amazon integration tests")
class AmazonIntegrationTest {

    private Database database;
    private ShoppingCartAdaptor shoppingCart;
    private Amazon amazon;

    @BeforeEach
    void setUp() {
        database = new Database();
        database.resetDatabase();

        shoppingCart = new ShoppingCartAdaptor(database);
        List<PriceRule> rules = List.of(
                new RegularCost(),
                new DeliveryPrice(),
                new ExtraCostForElectronics()
        );

        amazon = new Amazon(shoppingCart, rules);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Nested
    @DisplayName("Amazon.java")
    class AmazonFileTests {

        @Nested
        @DisplayName("specification-based")
        class SpecificationBased {

            @Test
            void calculateReturnsZeroWhenCartIsEmpty() {
                assertThat(amazon.calculate()).isEqualTo(0.0);
            }

            @Test
            void calculateAddsRegularAndDeliveryCostsForNonElectronicItems() {
                amazon.addToCart(new Item(ItemType.OTHER, "Book", 2, 10.0));

                assertThat(amazon.calculate()).isEqualTo(25.0);
            }

            @Test
            void calculateAppliesExtraCostWhenCartContainsElectronicItems() {
                amazon.addToCart(new Item(ItemType.ELECTRONIC, "Phone", 1, 100.0));
                amazon.addToCart(new Item(ItemType.OTHER, "Case", 1, 20.0));

                assertThat(amazon.calculate()).isEqualTo(132.5);
            }
        }

        @Nested
        @DisplayName("structural-based")
        class StructuralBased {

            @Test
            void calculateUsesDeliveryTierForFourItems() {
                amazon.addToCart(new Item(ItemType.OTHER, "Item 1", 1, 1.0));
                amazon.addToCart(new Item(ItemType.OTHER, "Item 2", 1, 1.0));
                amazon.addToCart(new Item(ItemType.OTHER, "Item 3", 1, 1.0));
                amazon.addToCart(new Item(ItemType.OTHER, "Item 4", 1, 1.0));

                assertThat(amazon.calculate()).isEqualTo(16.5);
            }

            @Test
            void calculateUsesHighestDeliveryTierForMoreThanTenRows() {
                for (int i = 1; i <= 11; i++) {
                    amazon.addToCart(new Item(ItemType.OTHER, "Item " + i, 1, 1.0));
                }

                assertThat(amazon.calculate()).isEqualTo(31.0);
            }
        }
    }

    @Nested
    @DisplayName("ShoppingCartAdaptor.java")
    class ShoppingCartAdaptorFileTests {

        @Nested
        @DisplayName("specification-based")
        class SpecificationBased {

            @Test
            void addToCartPersistsItemsInDatabaseBackedCart() {
                amazon.addToCart(new Item(ItemType.OTHER, "USB Cable", 3, 5.0));

                List<Item> items = shoppingCart.getItems();

                assertThat(items).hasSize(1);
                assertThat(items.getFirst().getName()).isEqualTo("USB Cable");
                assertThat(items.getFirst().getQuantity()).isEqualTo(3);
                assertThat(items.getFirst().getPricePerUnit()).isEqualTo(5.0);
                assertThat(items.getFirst().getType()).isEqualTo(ItemType.OTHER);
            }
        }

        @Nested
        @DisplayName("structural-based")
        class StructuralBased {

            @Test
            void getItemsReturnsAllRowsInsertedInOrder() {
                amazon.addToCart(new Item(ItemType.OTHER, "A", 1, 1.0));
                amazon.addToCart(new Item(ItemType.ELECTRONIC, "B", 2, 2.0));

                List<Item> items = shoppingCart.getItems();

                assertThat(items).hasSize(2);
                assertThat(items.get(0).getName()).isEqualTo("A");
                assertThat(items.get(1).getName()).isEqualTo("B");
            }
        }
    }

    @Nested
    @DisplayName("Database.java")
    class DatabaseFileTests {

        @Nested
        @DisplayName("specification-based")
        class SpecificationBased {

            @Test
            void resetDatabaseRemovesPreviouslyInsertedRows() {
                amazon.addToCart(new Item(ItemType.OTHER, "Temp", 1, 10.0));
                assertThat(shoppingCart.getItems()).hasSize(1);

                database.resetDatabase();

                assertThat(shoppingCart.getItems()).isEmpty();
            }

            @Test
            void withSqlReturnsSupplierValue() {
                String value = database.withSql(() -> "ok");

                assertThat(value).isEqualTo("ok");
            }
        }

        @Nested
        @DisplayName("structural-based")
        class StructuralBased {

            @Test
            void closeAndRecreateDatabaseStillAllowsOperations() {
                database.close();

                database = new Database();
                shoppingCart = new ShoppingCartAdaptor(database);
                amazon = new Amazon(shoppingCart, List.of(
                        new RegularCost(),
                        new DeliveryPrice(),
                        new ExtraCostForElectronics()
                ));

                amazon.addToCart(new Item(ItemType.OTHER, "Recovered", 1, 2.0));
                assertThat(shoppingCart.getItems()).hasSize(1);
            }

            @Test
            void withSqlWrapsSqlExceptionAsRuntimeException() {
                assertThatThrownBy(() -> database.withSql(() -> {
                    throw new SQLException("boom");
                }))
                        .isInstanceOf(RuntimeException.class)
                        .hasCauseInstanceOf(SQLException.class)
                        .hasRootCauseMessage("boom");
            }
        }
    }
}
