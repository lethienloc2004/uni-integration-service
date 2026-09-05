package vn.thanhdo.integration.sync;

/**
 * Ket qua mot lan hoi tu trang thai.
 *
 * <p>Gia tri {@link Action#NOOP} la BANG CHUNG TRUC QUAN cua tinh chong trung:
 * goi lan thu hai thay trang thai da dung nen khong lam gi. Khi bao ve, chi can
 * chieu dong nhat ky co {@code action=NOOP} la chung minh duoc kich ban T09.
 */
public record HandlerResult(Action action, String targetId, String detail) {

    public enum Action { CREATE, UPDATE, DELETE, NOOP }

    public static HandlerResult created(String targetId, String detail) {
        return new HandlerResult(Action.CREATE, targetId, detail);
    }

    public static HandlerResult updated(String targetId, String detail) {
        return new HandlerResult(Action.UPDATE, targetId, detail);
    }

    public static HandlerResult deleted(String targetId, String detail) {
        return new HandlerResult(Action.DELETE, targetId, detail);
    }

    public static HandlerResult noop(String targetId, String detail) {
        return new HandlerResult(Action.NOOP, targetId, detail);
    }

    public String actionName() {
        return action.name();
    }
}
