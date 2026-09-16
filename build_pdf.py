import os
import sys
import arabic_reshaper
from bidi.algorithm import get_display

from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, KeepTogether, HRFlowable
)
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_RIGHT, TA_CENTER, TA_LEFT
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

# Register DejaVuSans font which has full Arabic support
FONT_PATH = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FONT_BOLD_PATH = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'

pdfmetrics.registerFont(TTFont('ArabicFont', FONT_PATH))
pdfmetrics.registerFont(TTFont('ArabicFontBold', FONT_BOLD_PATH))

def ar(text):
    """Reshapes and applies BiDi algorithm for proper Arabic RTL rendering."""
    if not text:
        return ""
    # Replace newlines if inside table cell
    reshaped = arabic_reshaper.reshape(text)
    return get_display(reshaped)

def build_pdf(filename="FEATURES_SPECIFICATION.pdf"):
    doc = SimpleDocTemplate(
        filename,
        pagesize=A4,
        rightMargin=36,
        leftMargin=36,
        topMargin=36,
        bottomMargin=36
    )

    styles = getSampleStyleSheet()

    # Custom styles
    title_style = ParagraphStyle(
        'ArTitle',
        parent=styles['Normal'],
        fontName='ArabicFontBold',
        fontSize=20,
        leading=26,
        alignment=TA_CENTER,
        textColor=colors.HexColor('#0f172a')
    )

    subtitle_style = ParagraphStyle(
        'ArSubtitle',
        parent=styles['Normal'],
        fontName='ArabicFont',
        fontSize=12,
        leading=16,
        alignment=TA_CENTER,
        textColor=colors.HexColor('#2563eb')
    )

    h1_style = ParagraphStyle(
        'ArH1',
        parent=styles['Normal'],
        fontName='ArabicFontBold',
        fontSize=14,
        leading=19,
        alignment=TA_RIGHT,
        textColor=colors.HexColor('#1e3a8a'),
        spaceAfter=6,
        spaceBefore=12
    )

    h2_style = ParagraphStyle(
        'ArH2',
        parent=styles['Normal'],
        fontName='ArabicFontBold',
        fontSize=11,
        leading=15,
        alignment=TA_RIGHT,
        textColor=colors.HexColor('#0369a1'),
        spaceAfter=4,
        spaceBefore=8
    )

    body_style = ParagraphStyle(
        'ArBody',
        parent=styles['Normal'],
        fontName='ArabicFont',
        fontSize=9,
        leading=13,
        alignment=TA_RIGHT,
        textColor=colors.HexColor('#334155')
    )

    cell_style = ParagraphStyle(
        'ArCell',
        parent=styles['Normal'],
        fontName='ArabicFont',
        fontSize=8,
        leading=11,
        alignment=TA_RIGHT,
        textColor=colors.HexColor('#1e293b')
    )

    cell_bold = ParagraphStyle(
        'ArCellBold',
        parent=styles['Normal'],
        fontName='ArabicFontBold',
        fontSize=8.5,
        leading=12,
        alignment=TA_RIGHT,
        textColor=colors.HexColor('#0f172a')
    )

    cell_header = ParagraphStyle(
        'ArCellHeader',
        parent=styles['Normal'],
        fontName='ArabicFontBold',
        fontSize=9,
        leading=12,
        alignment=TA_CENTER,
        textColor=colors.white
    )

    elements = []

    # Title Banner
    elements.append(Paragraph(ar("ديوانية العراق - دليل الميزات وخارطة الطريق التقنية"), title_style))
    elements.append(Spacer(1, 4))
    elements.append(Paragraph(ar("Arabic Text Chat Rooms Platform - Features & Future Roadmap Specification"), subtitle_style))
    elements.append(Spacer(1, 8))
    elements.append(HRFlowable(width="100%", thickness=1.5, color=colors.HexColor('#2563eb'), spaceAfter=14))

    # Meta Info Box
    meta_data = [
        [
            Paragraph(ar("تاريخ التوثيق: 16 سبتمبر 2026"), cell_style),
            Paragraph(ar("المعمارية: Google MAD (Jetpack Compose) + Node.js"), cell_style),
            Paragraph(ar("الحالة العامة: جاهز للنشر والتسليم 100%"), cell_bold)
        ]
    ]
    meta_table = Table(meta_data, colWidths=[170, 200, 150])
    meta_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), colors.HexColor('#f8fafc')),
        ('BOX', (0,0), (-1,-1), 0.5, colors.HexColor('#cbd5e1')),
        ('ALIGN', (0,0), (-1,-1), 'RIGHT'),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('TOPPADDING', (0,0), (-1,-1), 6),
        ('BOTTOMPADDING', (0,0), (-1,-1), 6),
    ]))
    elements.append(meta_table)
    elements.append(Spacer(1, 14))

    # Section 1: Implemented Features Overview
    elements.append(Paragraph(ar("1. الميزات المنفذة حالياً وجاهزة للعمل بالكامل (100%)"), h1_style))
    elements.append(Paragraph(ar("يغطي النظام كافة شروط العميل الـ 9 بدقة فائقة وفق أفضل ممارسات الحماية وتجربة المستخدم الحديثة:"), body_style))
    elements.append(Spacer(1, 6))

    features_data = [
        [Paragraph(ar("حالة الميزة"), cell_header), Paragraph(ar("الوصف التقني والتشغيلي"), cell_header), Paragraph(ar("الميزة والمكون"), cell_header)],
        
        [Paragraph(ar("مكتمل وجاهز"), cell_bold),
         Paragraph(ar("تطبيق أصيل بالكامل يدعم العربية RTL، التدوير الطولي والعرضي، شريط علوي ملكي أزرق بـ 6 أزرار تفاعلية."), cell_style),
         Paragraph(ar("تطبيق أندرويد MAD Compose"), cell_bold)],

        [Paragraph(ar("مكتمل وجاهز"), cell_bold),
         Paragraph(ar("مشغل فيديو متزامن بالروم يعلو المحادثات، قابل للتصغير والإخفاء ومواصلة الدردشة أثناء الاستماع."), cell_style),
         Paragraph(ar("مشغل يوتيوب العائم (Condition 6)"), cell_bold)],

        [Paragraph(ar("مكتمل وجاهز"), cell_bold),
         Paragraph(ar("فقاعات تحاكي BoomChat، توقيت دقيق، وتمايز ألوان (👑 مدير ذهبي، 🛡️ مشرف بنفسجي، 💎 مميز وردي، 👤 عادي)."), cell_style),
         Paragraph(ar("فقاعات الرسائل ونظام الرتب"), cell_bold)],

        [Paragraph(ar("مكتمل وجاهز"), cell_bold),
         Paragraph(ar("النقر على أي رسالة أو اسم عضو ينسخ اسمه فوراً بخانة الكتابة مثل @الاسم: للرد السريع دون كتابة."), cell_style),
         Paragraph(ar("النقر السريع للمنشن (Quick-Mention)"), cell_bold)],

        [Paragraph(ar("مكتمل وجاهز"), cell_bold),
         Paragraph(ar("شخصية ذكية تطرح 12 لغزاً عراقياً وتراقب الشات لحظياً وتعلن الفائز وتمنحه 10 نقاط تلقائياً."), cell_style),
         Paragraph(ar("بوت المسابقات ست وداد 🤖"), cell_bold)],

        [Paragraph(ar("مكتمل وجاهز"), cell_bold),
         Paragraph(ar("منع الرسائل المتكررة، حظر إرسال رسالتين بأقل من ثانيتين، وكتم تلقائي للإغراق السريع (<800ms)."), cell_style),
         Paragraph(ar("محرك منع السبام وفلترة الروابط"), cell_bold)],

        [Paragraph(ar("مكتمل وجاهز"), cell_bold),
         Paragraph(ar("حظر صامت للمخرب يرى رسائله مرسلة بينما تُحجب تماماً عن بقية أعضاء الغرفة والسجلات."), cell_style),
         Paragraph(ar("وضع الشبح (Ghost Mode)"), cell_bold)],

        [Paragraph(ar("مكتمل وجاهز"), cell_bold),
         Paragraph(ar("حظر الجهاز بمعرف العتاد الفيزيائي الصارم لمنع الدخول حتى مع تغيير الاسم، مع كشف الحسابات البديلة."), cell_style),
         Paragraph(ar("حظر عتاد الجهاز (Device Ban)"), cell_bold)],

        [Paragraph(ar("مكتمل وجاهز"), cell_bold),
         Paragraph(ar("أيقونة درع للمشرفين: قفل العام، قفل الخاص، برودكاست عام، ترقية الرتب، وفك الكتم بنقرة واحدة."), cell_style),
         Paragraph(ar("لوحة الإشراف المدمجة بالتطبيق"), cell_bold)],

        [Paragraph(ar("مكتمل وجاهز"), cell_bold),
         Paragraph(ar("كتابة أوامر الإدارة في الشات مثل /mute و /kick و /quiz و /ban مع تنفيذ فوري وإشعار تأكيد خاص."), cell_style),
         Paragraph(ar("أوامر الإدارة المباشرة بالشات"), cell_bold)],

        [Paragraph(ar("مكتمل وجاهز"), cell_bold),
         Paragraph(ar("لوحة تحكم ويب عبر المتصفح تدعم تحديد عدة أعضاء وإجراء عمليات جماعية (فك كتم، كتم، طرد، ترقية)."), cell_style),
         Paragraph(ar("لوحة الويب الخارجية (/admin)"), cell_bold)]
    ]

    table_feat = Table(features_data, colWidths=[80, 290, 150])
    table_feat.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#1e3a8a')),
        ('ALIGN', (0,0), (-1,-1), 'RIGHT'),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor('#cbd5e1')),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, colors.HexColor('#f8fafc')]),
        ('TOPPADDING', (0,0), (-1,-1), 4),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
    ]))
    elements.append(table_feat)

    elements.append(PageBreak())

    # Section 2: Admin & Web Dashboard Details
    elements.append(Paragraph(ar("2. تفاصيل لوحة التحكم الخارجية والتحكم المتعدد بالأعضاء"), h1_style))
    elements.append(Paragraph(ar("تم تصميم لوحة الويب المركزية (Web Admin Dashboard) لتعمل على الرابط http://localhost:3001/admin وتوفر تحكماً شاملاً:"), body_style))
    elements.append(Spacer(1, 6))

    admin_details = [
        Paragraph(ar("• <b>التحكم الجماعي المتعدد (Bulk Actions):</b> تحديد عدة مستخدمين بمربعات الاختيار وفك الكتم، الكتم، الطرد، أو الترقية دفعة واحدة."), body_style),
        Paragraph(ar("• <b>مراقبة الغرف الخمس:</b> متابعة أعداد المتصلين، تبديل قفل الشات العام أو الخاص، وتغيير الإعلانات ومقاطع اليوتيوب."), body_style),
        Paragraph(ar("• <b>إرسال الإعلانات العامة (Broadcast):</b> نافذة لإرسال تنبيه فوري منبثق لجميع مستخدمي التطبيق في كافة الغرف."), body_style),
        Paragraph(ar("• <b>إدارة بوت المسابقات:</b> زر لطرح سؤال فوري في أي غرفة، مع جدول يعرض متصدري النقاط في المسابقات."), body_style),
        Paragraph(ar("• <b>إدارة الأجهزة المحظورة:</b> استعراض معرفات عتاد الأجهزة المحظورة وفك حظر أي جهاز بنقرة واحدة."), body_style),
        Paragraph(ar("• <b>موجه الأوامر السريع (CLI Terminal):</b> موجه أوامر مدمج يتيح تنفيذ أوامر السيرفر الفورية من داخل المتصفح."), body_style),
    ]
    for p in admin_details:
        elements.append(p)
        elements.append(Spacer(1, 3))

    elements.append(Spacer(1, 10))

    # Section 3: In-Chat Admin Commands
    elements.append(Paragraph(ar("3. أوامر الإدارة المباشرة داخل الشات (In-Chat Admin Commands)"), h1_style))
    cmd_data = [
        [Paragraph(ar("الوظيفة والإجراء"), cell_header), Paragraph(ar("الصيغة والمثال"), cell_header), Paragraph(ar("الأمر"), cell_header)],
        [Paragraph(ar("كتم العضو فوراً ومنعه من الإرسال"), cell_style), Paragraph(ar("/mute <الاسم> (مثال: /mute حسوني)"), cell_style), Paragraph(ar("/mute أو /كتم"), cell_bold)],
        [Paragraph(ar("فك الكتم وتفعيل العضو"), cell_style), Paragraph(ar("/unmute <الاسم> (مثال: /unmute حسوني)"), cell_style), Paragraph(ar("/unmute أو /فك_كتم"), cell_bold)],
        [Paragraph(ar("تفعيل وضع الشبح الصامت"), cell_style), Paragraph(ar("/ghost <الاسم> (مثال: /ghost زائر12)"), cell_style), Paragraph(ar("/ghost أو /شبح"), cell_bold)],
        [Paragraph(ar("إلغاء وضع الشبح عن العضو"), cell_style), Paragraph(ar("/unghost <الاسم>"), cell_style), Paragraph(ar("/unghost أو /الغاء_شبح"), cell_bold)],
        [Paragraph(ar("طرد العضو وفصل اتصاله بالغرفة"), cell_style), Paragraph(ar("/kick <الاسم>"), cell_style), Paragraph(ar("/kick أو /طرد"), cell_bold)],
        [Paragraph(ar("حظر عتاد الجهاز الفيزيائي نهائياً"), cell_style), Paragraph(ar("/ban <الاسم>"), cell_style), Paragraph(ar("/ban أو /حظر"), cell_bold)],
        [Paragraph(ar("ترقية الرتبة (MODERATOR / VIP_DIAMOND / REGULAR)"), cell_style), Paragraph(ar("/promote <الاسم> <الرتبة>"), cell_style), Paragraph(ar("/promote أو /ترقية"), cell_bold)],
        [Paragraph(ar("طرح سؤال مسابقة فوراً بواسطة ست وداد"), cell_style), Paragraph(ar("/quiz"), cell_style), Paragraph(ar("/quiz أو /مسابقة"), cell_bold)],
        [Paragraph(ar("إرسال إشعار برودكاست لجميع الغرف"), cell_style), Paragraph(ar("/broadcast <النص>"), cell_style), Paragraph(ar("/broadcast أو /اعلان"), cell_bold)],
        [Paragraph(ar("قفل أو فتح الشات العام بالروم"), cell_style), Paragraph(ar("/lock public on أو /lock public off"), cell_style), Paragraph(ar("/lock أو /قفل"), cell_bold)],
        [Paragraph(ar("تغيير إعلان الروم الشريطي فوراً"), cell_style), Paragraph(ar("/topic <النص>"), cell_style), Paragraph(ar("/topic أو /اعلان_الروم"), cell_bold)]
    ]
    table_cmd = Table(cmd_data, colWidths=[180, 200, 140])
    table_cmd.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#0369a1')),
        ('ALIGN', (0,0), (-1,-1), 'RIGHT'),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor('#cbd5e1')),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, colors.HexColor('#f8fafc')]),
        ('TOPPADDING', (0,0), (-1,-1), 3.5),
        ('BOTTOMPADDING', (0,0), (-1,-1), 3.5),
    ]))
    elements.append(table_cmd)

    elements.append(PageBreak())

    # Section 4: Future Roadmap
    elements.append(Paragraph(ar("4. الميزات المستقبلية وخارطة طريق التطوير (Future Roadmap)"), h1_style))
    elements.append(Paragraph(ar("ميزات مقترحة هندسياً لنقل التطبيق إلى مصاف التطبيقات العالمية (مثل Yalla و Clubhouse):"), body_style))
    elements.append(Spacer(1, 8))

    roadmap_data = [
        [Paragraph(ar("الأثر التقني والجدوى"), cell_header), Paragraph(ar("تفاصيل التطوير المقترحة"), cell_header), Paragraph(ar("المرحلة والميزة"), cell_header)],
        
        [Paragraph(ar("تجربة تفاعلية سريعة ومريحة للأعضاء"), cell_style),
         Paragraph(ar("تسجيل مقاطع صوتية بالضغط المطول وضغطها بصيغة Opus خفيفة مع مشغل مدمج ورسم للموجات الصوتية."), cell_style),
         Paragraph(ar("1. الرسائل الصوتية (Voice Notes PTT)"), cell_bold)],

        [Paragraph(ar("مشاركة الصور بأمان وسرعة"), cell_style),
         Paragraph(ar("إرسال الصور والفيديوهات المصغرة وتخزينها سحابياً عبر Cloudflare R2 / AWS S3 مع فحص المحتوى الحساس."), cell_style),
         Paragraph(ar("2. وسائط الصور والفيديو (Media CDN)"), cell_bold)],

        [Paragraph(ar("تسييل مالي وربح مباشر لصاحب التطبيق"), cell_style),
         Paragraph(ar("إرسال هدايا متحركة (ورود، سيارات، تيجان) تظهر بشاشات متحركة Lottie مع محفظة كوينز لشحن الرصيد."), cell_style),
         Paragraph(ar("3. متجر الهدايا الرقمية (Gifts & Store)"), cell_bold)],

        [Paragraph(ar("قفزة نوعية في تفاعل الغرف"), cell_style),
         Paragraph(ar("تمكين الأعضاء من الصعود على مايكات صوتية مباشرة بتقنية WebRTC والتحدث الحي مع الحفاظ على الشات."), cell_style),
         Paragraph(ar("4. الرومات الصوتية الحية (Voice Rooms)"), cell_bold)],

        [Paragraph(ar("تسهيل عمليات الدفع للمستخدمين في العراق"), cell_style),
         Paragraph(ar("ربط زين كاش، آسيا حوالة، كي كارد، و Google Play In-App Billing للاشتراكات وشحن الرصيد."), cell_style),
         Paragraph(ar("5. بوابات الدفع المحلية والدولية"), cell_bold)],

        [Paragraph(ar("حماية آلية ذكية بدون تدخل بشري"), cell_style),
         Paragraph(ar("كشف التنمر والتحايل الإملائي عبر نماذج الذكاء الاصطناعي، وكشف شبكات الـ VPN والبروكسيات المشبوهة."), cell_style),
         Paragraph(ar("6. الإشراف الذكي بالـ AI والحظر الجغرافي"), cell_bold)]
    ]

    table_road = Table(roadmap_data, colWidths=[120, 260, 140])
    table_road.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#4338ca')),
        ('ALIGN', (0,0), (-1,-1), 'RIGHT'),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor('#cbd5e1')),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, colors.HexColor('#f8fafc')]),
        ('TOPPADDING', (0,0), (-1,-1), 5),
        ('BOTTOMPADDING', (0,0), (-1,-1), 5),
    ]))
    elements.append(table_road)

    elements.append(Spacer(1, 14))

    # Section 5: Verification & APK delivery info
    elements.append(Paragraph(ar("5. معلومات التسليم وحالة الجاهزية الحالية"), h1_style))
    
    deliv_items = [
        Paragraph(ar("• بيانات حساب المدير: الاسم: ali (أو علي) | كلمة المرور: demo123 (رتبة OWNER)"), cell_bold),
        Paragraph(ar("• رابط لوحة التحكم الخارجية: http://localhost:3001/admin"), cell_style),
        Paragraph(ar("• ملف التطبيق الجاهز: /sdcard/Download/ali-chat-debug.apk (الحجم: 19.4 MB)"), cell_style),
        Paragraph(ar("• نتائج الاختبارات الآلية: نجاح كامل 33 من أصل 33 اختبار e2e أخضر بنسبة 100%"), cell_style)
    ]
    
    delivery_table_data = [[p] for p in deliv_items]
    deliv_table = Table(delivery_table_data, colWidths=[520])
    deliv_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), colors.HexColor('#ecfdf5')),
        ('BOX', (0,0), (-1,-1), 1, colors.HexColor('#10b981')),
        ('ALIGN', (0,0), (-1,-1), 'RIGHT'),
        ('TOPPADDING', (0,0), (-1,-1), 4),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
    ]))
    elements.append(deliv_table)

    doc.build(elements)
    print(f"Document {filename} generated successfully!")

if __name__ == '__main__':
    build_pdf()
