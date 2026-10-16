package tj.railway.mesplan.file;

/** Файл заявок не разобран. Сообщение — для оператора, по-русски, с местом ошибки. */
public class OrderFormatException extends RuntimeException {
    public OrderFormatException(String message) {
        super(message);
    }
}
