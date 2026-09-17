package io.github.jevonsnotes.jevokit.core.util;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.github.jevonsnotes.jevokit.core.dto.SqlBundleDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SqlUtil} 单元测试：选择性 Insert/Update/Select 预编译 SQL 生成（含表名、列名映射与防御性校验）。
 */
@DisplayName("SqlUtil 预编译 SQL 生成")
class SqlUtilTest {

    @Table(name = "t_user")
    static class UserEntity {
        @Column(name = "user_name")
        String userName;
        Integer age;
        String homeAddress;
        Long id;
        static String status = "ACTIVE";
    }

    /** 无 @Table 注解的实体：表名回退到类名 */
    static class PlainEntity {
        String name;
    }

    private static UserEntity fullUser() {
        UserEntity user = new UserEntity();
        user.userName = "alice";
        user.age = 30;
        user.homeAddress = "earth";
        user.id = 1L;
        return user;
    }

    // ------------------------------------------------------------------
    // generateInsertSelectivePreSql
    // ------------------------------------------------------------------

    @Test
    @DisplayName("insert：注解列名优先，camelCase 自动转下划线，静态字段跳过")
    void insertSelectiveFull() {
        // given
        UserEntity user = fullUser();
        // when
        SqlBundleDto bundle = SqlUtil.generateInsertSelectivePreSql(user);
        // then
        assertThat(bundle.getSql())
                .as("应使用 @Table 表名并生成 insert 骨架")
                .contains("Insert into t_user")
                .contains("user_name").as("@Column 指定列名优先")
                .contains("home_address").as("camelCase 字段应转下划线列名")
                .contains("?").contains(")");
        assertThat(bundle.getArgs())
                .as("参数应按字段顺序收集且不含静态字段值")
                .containsExactly("alice", 30, "earth", 1L);
    }

    @Test
    @DisplayName("insert：null 字段被跳过（selective 语义）")
    void insertSelectiveSkipsNull() {
        // given：仅 userName 与 id 有值
        UserEntity user = new UserEntity();
        user.userName = "bob";
        user.id = 2L;
        // when
        SqlBundleDto bundle = SqlUtil.generateInsertSelectivePreSql(user);
        // then
        assertThat(bundle.getSql()).as("age 列应缺失").doesNotContain("age");
        assertThat(bundle.getArgs()).as("只收集非空字段值").containsExactly("bob", 2L);
    }

    @Test
    @DisplayName("insert：无 @Table 实体回退类名；全部字段为空拒绝生成")
    void insertFallbackTableAndRejectEmpty() {
        // given：name 无 @Table 无 @Column，走类名表 + camelCase 列
        PlainEntity entity = new PlainEntity();
        entity.name = "x";
        // when
        SqlBundleDto bundle = SqlUtil.generateInsertSelectivePreSql(entity);
        // then
        assertThat(bundle.getSql()).as("无注解时表名用类名").contains("Insert into PlainEntity");

        // and：全字段为 null 的实体
        assertThatThrownBy(() -> SqlUtil.generateInsertSelectivePreSql(new PlainEntity()))
                .as("无任何非空字段应拒绝生成 insert")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无数据可新增");
    }

    // ------------------------------------------------------------------
    // generateUpdateSelectivePreSql
    // ------------------------------------------------------------------

    @Test
    @DisplayName("update：where 列进 where 子句，其余进 set 子句，参数先 set 后 where")
    void updateSelective() {
        // given
        UserEntity user = fullUser();
        // when
        SqlBundleDto bundle = SqlUtil.generateUpdateSelectivePreSql(user, new String[]{"ID"});
        // then：whereColumns 大小写不敏感（列名统一小写比较）
        assertThat(bundle.getSql())
                .as("应生成 update 骨架并带 where 条件")
                .contains("Update t_user")
                .contains("user_name = ?")
                .contains("where 1 =1 ")
                .contains("and id = ?")
                .doesNotContain("set id");
        assertThat(bundle.getArgs())
                .as("参数顺序应为 set 值在前、where 值在后")
                .containsExactly("alice", 30, "earth", 1L);
    }

    @Test
    @DisplayName("update：whereColumns 为空/null 拒绝生成")
    void updateRejectsEmptyWhereColumns() {
        UserEntity user = fullUser();
        assertThatThrownBy(() -> SqlUtil.generateUpdateSelectivePreSql(user, new String[0]))
                .as("空 whereColumns 应拒绝").isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("条件字段不能为空");
        assertThatThrownBy(() -> SqlUtil.generateUpdateSelectivePreSql(user, null))
                .as("null whereColumns 应拒绝").isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("条件字段不能为空");
    }

    @Test
    @DisplayName("update：where 列值为 null 视为全表更新，拒绝")
    void updateRejectsNullWhereValue() {
        // given：id 为 null，其余字段有值
        UserEntity user = fullUser();
        user.id = null;
        // then
        assertThatThrownBy(() -> SqlUtil.generateUpdateSelectivePreSql(user, new String[]{"id"}))
                .as("where 值为 null 会退化为全表更新，应拒绝")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("禁止全量更新");
    }

    @Test
    @DisplayName("update：除 where 列外无可更新字段时拒绝")
    void updateRejectsNoSettableField() {
        // given：仅 id 有值
        UserEntity user = new UserEntity();
        user.id = 9L;
        // then
        assertThatThrownBy(() -> SqlUtil.generateUpdateSelectivePreSql(user, new String[]{"id"}))
                .as("没有 set 字段应拒绝")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无可更新字段");
    }

    // ------------------------------------------------------------------
    // generateSelectSelectivePreSql
    // ------------------------------------------------------------------

    @Test
    @DisplayName("select：列出全部非静态列，where 仅含非空的条件列")
    void selectSelective() {
        // given：仅 userName 有值
        UserEntity user = new UserEntity();
        user.userName = "alice";
        user.id = 1L;
        // when
        SqlBundleDto bundle = SqlUtil.generateSelectSelectivePreSql(user, new String[]{"user_name", "home_address", "age"});
        // then
        assertThat(bundle.getSql())
                .as("应生成 select 骨架并列出全部非静态列")
                .contains("Select ")
                .contains("from t_user")
                .contains("user_name").contains("age").contains("home_address").contains("id")
                .contains("and user_name = ?")
                .doesNotContain("and home_address = ?").as("值为 null 的条件列不应进 where")
                .doesNotContain("and id = ?").as("非条件列不进 where");
        assertThat(bundle.getArgs()).as("select 参数只有 where 值").containsExactly("alice");
    }

    @Test
    @DisplayName("select：无 where 值时仍生成全列查询（where 1=1 恒真）")
    void selectWithoutWhereValues() {
        // given：实体所有字段为 null
        UserEntity user = new UserEntity();
        // when
        SqlBundleDto bundle = SqlUtil.generateSelectSelectivePreSql(user, new String[]{"id"});
        // then
        assertThat(bundle.getSql())
                .as("无条件值时应保留 where 1=1 骨架")
                .contains("where 1 =1")
                .doesNotContain("and ");
        assertThat(bundle.getArgs()).as("无 where 参数").isEmpty();
    }
}
