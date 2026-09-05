package vn.thanhdo.integration.sync;

import vn.thanhdo.integration.inbox.InboxEvent;

/**
 * Bo xu ly mot nhom su kien.
 *
 * <p>Ba bo xu ly theo huong SIS -> LMS deu la HAM HOI TU TRANG THAI: chung khong
 * hoi "su kien nay bao toi lam gi" ma hoi "trang thai dung phai nhu the nao, va
 * hien tai no dang ra sao". Nho vay chong trung va kha nang chiu su kien sai thu tu
 * tro thanh thuoc tinh tu nhien cua thiet ke chu khong phai phan cai them.
 */
public interface EventHandler {

    /** Ten dung trong nhat ky, vi du "ensureUser". */
    String name();

    /** Chieu ghi, dung cho nhat ky kiem toan: SIS_TO_LMS hoac LMS_TO_SIS. */
    String direction();

    boolean supports(String eventType);

    HandlerResult handle(InboxEvent event);
}
