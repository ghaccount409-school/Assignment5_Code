package org.example.Amazon;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

            @Test
            void calculateUsesItemQuantityForRegularCostButRowCountForDelivery() {
                amazon.addToCart(new Item(ItemType.OTHER, "Bulk pencils", 10, 2.0));

                assertThat(amazon.calculate()).isEqualTo(25.0);
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
                assertThat(items.get(1).getType()).isEqualTo(ItemType.ELECTRONIC);
                assertThat(items.get(1).getQuantity()).isEqualTo(2);
                assertThat(items.get(1).getPricePerUnit()).isEqualTo(2.0);
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

            @Test
            void withSqlCanQueryPersistedRows() {
                amazon.addToCart(new Item(ItemType.OTHER, "Desk", 1, 99.0));

                Integer count = database.withSql(() -> {
                    try (var ps = database.getConnection().prepareStatement("select count(*) from shoppingcart")) {
                        var rs = ps.executeQuery();
                        rs.next();
                        return rs.getInt(1);
                    }
                });

                assertThat(count).isEqualTo(1);
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
            void constructorReusesConnectionWhileOpenAndAllowsUseAfterReopen() {
                Database first = new Database();
                Database second = new Database();

                assertThat(second.getConnection()).isSameAs(first.getConnection());

                first.close();
                Database reopened = new Database();

                Integer value = reopened.withSql(() -> 1);
                assertThat(value).isEqualTo(1);
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

            @Test
            void separateAdaptorInstancesSeeSameStoredRows() {
                amazon.addToCart(new Item(ItemType.OTHER, "Shared", 1, 3.0));

                ShoppingCartAdaptor secondAdaptor = new ShoppingCartAdaptor(new Database());

                assertThat(secondAdaptor.getItems()).hasSize(1);
                assertThat(secondAdaptor.getItems().getFirst().getName()).isEqualTo("Shared");
            }
        }
    }
}
