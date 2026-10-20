package com.fruitude.faq;

import com.fruitude.faq.model.*;
import com.fruitude.faq.controller.AdminFaqController;
import com.fruitude.promo.model.PromoRepository;
import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

public class FaqIntegrationTest {
    private static class Fixture {
        final Map<Integer,FaqCategory> categories=new LinkedHashMap<>();
        final Map<Integer,Faq> faqs=new LinkedHashMap<>();
        int nextCategory=10,nextFaq=20;
        final FaqCategoryRepository categoryRepository=proxy(FaqCategoryRepository.class,this::categoryCall);
        final FaqRepository faqRepository=proxy(FaqRepository.class,this::faqCall);
        final PromoRepository promos=proxy(PromoRepository.class,(name,args)->List.of());
        final FaqService service=new FaqService(faqRepository,categoryRepository,promos);
        Fixture(){category(1,"商品",null);category(2,"配送",null);}
        FaqCategory category(int id,String name,Integer parent){
            var c=new FaqCategory();c.setFaqCategoryId(id);c.setCategoryName(name);c.setStatus(1);
            c.setParentCategory(parent==null?null:categories.get(parent));categories.put(id,c);return c;
        }
        Faq faq(int id,int category,String question){
            var f=new Faq();f.setFaqId(id);f.setFaqCategory(categories.get(category));f.setQuestion(question);
            f.setAnswer("請先確認商品及配送資料。");f.setKeyword("配送&日期");f.setSortOrder(id);faqs.put(id,f);return f;
        }
        Integer parent(FaqCategory c){return c.getParentCategory()==null?null:c.getParentCategory().getFaqCategoryId();}
        boolean active(FaqCategory c){
            var seen=new HashSet<Integer>();
            while(c!=null){if(!seen.add(c.getFaqCategoryId())||!Integer.valueOf(1).equals(c.getStatus()))return false;c=c.getParentCategory();}
            return true;
        }
        Object categoryCall(String name,Object[] args){
            return switch(name){
                case "findById" -> Optional.ofNullable(categories.get(args[0]));
                case "existsSiblingName" -> categories.values().stream().anyMatch(c->c.getCategoryName().equalsIgnoreCase((String)args[0])
                    &&Objects.equals(parent(c),args[1])&&!Objects.equals(c.getFaqCategoryId(),args[2]));
                case "findAllByOrderByFaqCategoryIdAsc" -> new ArrayList<>(categories.values());
                case "existsByParentCategory_FaqCategoryId" -> categories.values().stream().anyMatch(c->Objects.equals(parent(c),args[0]));
                case "saveAndFlush" -> {var c=(FaqCategory)args[0];if(c.getFaqCategoryId()==null)c.setFaqCategoryId(nextCategory++);categories.put(c.getFaqCategoryId(),c);yield c;}
                case "deleteById" -> {categories.remove(args[0]);yield null;}
                default -> throw new AssertionError(name);
            };
        }
        Object faqCall(String name,Object[] args){
            return switch(name){
                case "findById" -> Optional.ofNullable(faqs.get(args[0]));
                case "findForAdministration" -> new ArrayList<>(faqs.values());
                case "findByCategoryForAdministration" -> faqs.values().stream().filter(f->Objects.equals(f.getFaqCategory().getFaqCategoryId(),args[0])).toList();
                case "findAvailableForReply" -> faqs.values().stream().filter(f->active(f.getFaqCategory())).toList();
                case "existsByFaqCategory_FaqCategoryId" -> faqs.values().stream().anyMatch(f->Objects.equals(f.getFaqCategory().getFaqCategoryId(),args[0]));
                case "existsByFaqCategory_FaqCategoryIdAndQuestion", "existsByFaqCategory_FaqCategoryIdAndQuestionAndFaqIdNot" -> faqs.values().stream().anyMatch(f->Objects.equals(f.getFaqCategory().getFaqCategoryId(),args[0])
                    &&f.getQuestion().equals(args[1])&&(args.length<3||!Objects.equals(f.getFaqId(),args[2])));
                case "findMaxSortOrder" -> faqs.values().stream().filter(f->Objects.equals(f.getFaqCategory().getFaqCategoryId(),args[0])).mapToInt(Faq::getSortOrder).max().orElse(0);
                case "saveAndFlush" -> {var f=(Faq)args[0];if(f.getFaqId()==null)f.setFaqId(nextFaq++);faqs.put(f.getFaqId(),f);yield f;}
                case "saveAll" -> args[0];
                case "delete" -> {faqs.remove(((Faq)args[0]).getFaqId());yield null;}
                default -> throw new AssertionError(name);
            };
        }
    }
    private interface Call {Object invoke(String name,Object[] args);}
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,Call call){
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(o,m,a)->call.invoke(m.getName(),a==null?new Object[0]:a));
    }
    private static void rejected(Runnable action,String text){
        try{action.run();fail("Expected rejection");}catch(IllegalArgumentException e){assertTrue(e.getMessage(),e.getMessage().contains(text));}
    }
    @Test public void siblingAndRootDuplicatesAreRejectedButDifferentParentsMayShareNames(){
        var f=new Fixture();
        var a=f.service.addCategory(new FaqService.CategoryForm(" 常見問題 ",null,1));
        var b=f.service.addCategory(new FaqService.CategoryForm("常見問題",null,2));
        assertNotEquals(a.faqCategoryId(),b.faqCategoryId());assertEquals(Integer.valueOf(1),a.parentCategoryId());
        assertEquals(Integer.valueOf(0),a.status());
        rejected(()->f.service.addCategory(new FaqService.CategoryForm("常見問題",null,1)),"相同");
        rejected(()->f.service.addCategory(new FaqService.CategoryForm("商品",null,null)),"相同");
        assertEquals(a.faqCategoryId(),f.service.updateCategory(a.faqCategoryId(),new FaqService.CategoryForm("常見問題","說明",1)).faqCategoryId());
        rejected(()->f.service.updateCategory(b.faqCategoryId(),new FaqService.CategoryForm("常見問題",null,1)),"相同");
        assertEquals(Integer.valueOf(2),f.categories.get(b.faqCategoryId()).getParentCategory().getFaqCategoryId());
    }
    @Test public void reparentingChecksDuplicateRootsAndHierarchyCycles(){
        var f=new Fixture();f.category(3,"商品",2);f.category(4,"子分類",3);
        rejected(()->f.service.updateCategory(3,new FaqService.CategoryForm("商品",null,null)),"相同");
        rejected(()->f.service.updateCategory(3,new FaqService.CategoryForm("商品",null,4)),"上層");
        rejected(()->f.service.updateCategory(3,new FaqService.CategoryForm("商品",null,3)),"上層");
        f.categories.get(2).setParentCategory(f.categories.get(4));
        rejected(()->f.service.addCategory(new FaqService.CategoryForm("新類別",null,2)),"上層");
    }
    @Test public void managementChangesImmediatelyFeedRepliesAndAncestorStatusIsRespected(){
        var f=new Fixture();f.category(3,"保存",1);
        var created=f.service.addFaq(new FaqService.FaqForm(3,"如何保存？","請冷藏保存。","保存&水果"));
        assertEquals("請冷藏保存。",f.service.reply("水果怎麼保存"));
        f.service.updateFaq(created.faqId(),new FaqService.FaqForm(3,"如何保存？","依品項冷藏或常溫保存。","保存&水果"));
        assertEquals("依品項冷藏或常溫保存。",f.service.reply("水果保存"));
        f.service.setCategoryStatus(1,false);assertNull(f.service.reply("水果保存"));
        f.service.setCategoryStatus(1,true);assertNotNull(f.service.reply("水果保存"));
        f.service.deleteFaq(created.faqId());assertNull(f.service.reply("水果保存"));
    }
    @Test public void protectedLiveQuestionsStillUseCurrentPromotionData(){
        var f=new Fixture();var faq=f.faq(3,1,"生日優惠是幾折？");faq.setKeyword("生日");
        rejected(()->f.service.updateFaq(3,new FaqService.FaqForm(1,"改題目","答案","生日")),"不能修改");
        rejected(()->f.service.deleteFaq(3),"不能刪除");
        assertTrue(f.service.reply("生日").contains("目前沒有進行中的這類優惠"));
        assertTrue(FaqService.liveQuestions().contains("生日優惠是幾折？"));
    }
    @Test public void orderingMembershipAndCategoryDeletionRemainProtected(){
        var f=new Fixture();f.faq(3,1,"問題一");f.faq(4,1,"問題二");
        rejected(()->f.service.reorderFaqs(1,List.of(3,3)),"重新整理");
        rejected(()->f.service.reorderFaqs(1,List.of(3,99)),"重新整理");
        f.service.reorderFaqs(1,List.of(4,3));assertEquals(Integer.valueOf(1),f.faqs.get(4).getSortOrder());
        rejected(()->f.service.deleteCategory(1),"FAQ");
        f.category(5,"子分類",2);rejected(()->f.service.deleteCategory(2),"子類別");
        f.service.deleteCategory(5);assertFalse(f.categories.containsKey(5));
    }
    @Test public void existingRoutesReturnFlatJsonAndEnforcePermission()throws Exception{
        var f=new Fixture();f.category(3,"保存",1);f.faq(3,3,"保存問題");
        var controller=new AdminFaqController();ReflectionTestUtils.setField(controller,"faqSvc",f.service);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        assertEquals(403,mvc.perform(get("/api/admin/chat/faq/categories")).andReturn().getResponse().getStatus());
        var categories=mvc.perform(get("/api/admin/chat/faq/categories").requestAttr("adminPermissionCodes",Set.of("CHAT_REPLY"))).andReturn().getResponse();
        assertEquals(200,categories.getStatus());assertTrue(categories.getContentAsString().contains("\"parentCategoryId\":1"));assertFalse(categories.getContentAsString().contains("\"parentCategory\":"));
        var items=mvc.perform(get("/api/admin/chat/faq/items").requestAttr("adminPermissionCodes",Set.of("CHAT_REPLY"))).andReturn().getResponse();
        assertEquals(200,items.getStatus());assertTrue(items.getContentAsString().contains("\"faqCategoryId\":3"));assertFalse(items.getContentAsString().contains("\"faqCategory\":"));
        var duplicate=mvc.perform(post("/api/admin/chat/faq/categories").requestAttr("adminPermissionCodes",Set.of("CHAT_REPLY"))
            .contentType("application/json").content("{\"categoryName\":\"保存\",\"parentCategoryId\":1}")).andReturn().getResponse();
        assertEquals(400,duplicate.getStatus());assertTrue(duplicate.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("同一上層"));
        assertEquals("admin/chat/faq",mvc.perform(get("/admin/chat/faq").requestAttr("adminPermissionCodes",Set.of("CHAT_REPLY"))).andReturn().getModelAndView().getViewName());
    }
    @Test public void entityRelationsAndRepositoryQueriesValidateWithoutDatabase(){
        var factory=new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
        factory.setPersistenceUnitName("faq-merge-validation");
        factory.setManagedTypes(org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes.of(Faq.class.getName(),FaqCategory.class.getName()));
        factory.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.dialect","org.hibernate.dialect.MySQLDialect",
            "hibernate.boot.allow_jdbc_metadata_access","false","hibernate.hbm2ddl.auto","none"));
        factory.afterPropertiesSet();
        var em=factory.getObject().createEntityManager();
        try{
            var repositories=new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em);
            assertNotNull(repositories.getRepository(FaqRepository.class));
            assertNotNull(repositories.getRepository(FaqCategoryRepository.class));
        }finally{em.close();factory.destroy();}
    }
}
