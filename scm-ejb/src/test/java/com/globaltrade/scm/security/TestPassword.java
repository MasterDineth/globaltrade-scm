import com.globaltrade.scm.security.SecurityUtil;
public class TestPassword {
    public static void main(String[] args) {
        String hash = "120000:L7IiZAnc71nceASwe9PbOA==:cNRbwpGMYWnivBYe9r977tvIaVv2HSdzmJEJhHbPM8s=";
        String[] guesses = {"password", "wmanager", "wmanager123", "admin", "admin123", "123456", "password123"};
        for (String guess : guesses) {
            if (SecurityUtil.verifyPassword(guess, hash)) {
                System.out.println("Password is: " + guess);
                return;
            }
        }
        System.out.println("New hash for 'password': " + SecurityUtil.hashPassword("password"));
    }
}
